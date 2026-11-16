package com.example.usermanagement.repository;

import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picked up automatically by Spring Data because of the "Impl" suffix.
 */
public class UserSearchRepositoryImpl implements UserSearchRepository {

    private final EntityManager em;

    public UserSearchRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Page<UserResponse> search(String q, Boolean active, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<UserResponse> cq = cb.createQuery(UserResponse.class);
        Root<User> root = cq.from(User.class);

        cq.select(construct(cb, root));
        cq.where(predicates(cb, root, q, active, null));
        cq.orderBy(orders(cb, root, pageable.getSort()));

        TypedQuery<UserResponse> query = em.createQuery(cq);
        if (pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        List<UserResponse> content = query.getResultList();

        // skips the COUNT when this is the first page and it is not full (or the last page is short)
        return PageableExecutionUtils.getPage(content, pageable, () -> count(q, active));
    }

    @Override
    public List<UserResponse> searchAfter(String q, Boolean active, Long afterId, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<UserResponse> cq = cb.createQuery(UserResponse.class);
        Root<User> root = cq.from(User.class);

        cq.select(construct(cb, root));
        cq.where(predicates(cb, root, q, active, afterId));
        cq.orderBy(cb.asc(root.get("id")));

        return em.createQuery(cq).setMaxResults(limit).getResultList();
    }

    private long count(String q, Boolean active) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<User> root = cq.from(User.class);
        cq.select(cb.count(root));
        cq.where(predicates(cb, root, q, active, null));
        return em.createQuery(cq).getSingleResult();
    }

    /**
     * SELECT only the columns the response needs (constructor expression on the record).
     */
    private static jakarta.persistence.criteria.CompoundSelection<UserResponse> construct(
            CriteriaBuilder cb, Root<User> root) {
        return cb.construct(UserResponse.class,
                root.get("id"),
                root.get("username"),
                root.get("email"),
                root.get("firstName"),
                root.get("lastName"),
                root.get("active"),
                root.get("createdAt"),
                root.get("updatedAt"));
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<User> root,
                                          String q, Boolean active, Long afterId) {
        List<Predicate> predicates = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            // lower(col) LIKE '%q%' matches the PostgreSQL trigram indexes created in V5
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern)));
        }
        if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
        }
        if (afterId != null) {
            predicates.add(cb.greaterThan(root.<Long>get("id"), afterId));
        }
        return predicates.toArray(new Predicate[0]);
    }

    /**
     * Sort properties are validated by the service layer; id is always appended as a tie-breaker.
     */
    private static List<Order> orders(CriteriaBuilder cb, Root<User> root, Sort sort) {
        List<Order> orders = new ArrayList<>();
        boolean sortedById = false;
        for (Sort.Order order : sort) {
            orders.add(order.isAscending()
                    ? cb.asc(root.get(order.getProperty()))
                    : cb.desc(root.get(order.getProperty())));
            sortedById |= "id".equals(order.getProperty());
        }
        if (!sortedById) {
            orders.add(cb.asc(root.get("id"))); // deterministic paging
        }
        return orders;
    }
}
