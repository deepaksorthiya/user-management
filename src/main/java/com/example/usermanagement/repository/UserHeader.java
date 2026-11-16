package com.example.usermanagement.repository;

/**
 * Closed interface projection: Spring Data selects only these two columns.
 */
public interface UserHeader {

    String getUsername();

    Boolean getActive();
}
