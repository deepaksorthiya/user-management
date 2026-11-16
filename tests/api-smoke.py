"""Exercise every API operation against a running disposable test instance.
Run: python tests/api-smoke.py [http://127.0.0.1:8080]
Creates uniquely named records and deletes them in finally; never modifies sample records.
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080').rstrip('/')
# Local app traffic must not go through the environment's outbound proxy.
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
checks = 0
created = []
tag = str(time.time_ns())

def check(value, message):
    global checks
    assert value, message
    checks += 1

def request(path, method='GET', body=None, expected=200, api=True):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + ('/api/v1/' if api else '/') + path, data=data,
                                 headers={'Content-Type': 'application/json'} if data else {}, method=method)
    try:
        with opener.open(req, timeout=20) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    check(status == expected, f'{method} {path}: expected {expected}, got {status}: {raw[:300]!r}')
    if not raw:
        return None
    if not api:
        return raw.decode()
    return json.loads(raw)

def create(kind, body):
    item = request(kind, 'POST', body, 201)
    created.append((kind, item['id']))
    return item

def ids(items):
    return {item['id'] for item in items}

def linked(path, target_id, read_path=None, paged=False):
    request(f'{path}/{target_id}', 'PUT', expected=204)
    request(f'{path}/{target_id}', 'PUT', expected=204)
    result = request(read_path or path)
    check(target_id in ids(result['content'] if paged else result), f'Assignment missing: {path}')
    request(f'{path}/{target_id}', 'DELETE', expected=204)
    request(f'{path}/{target_id}', 'DELETE', expected=204)
    result = request(read_path or path)
    check(target_id not in ids(result['content'] if paged else result), f'Assignment remained: {path}')

def replace(path, target_id):
    result = request(path, 'PUT', {'ids':[target_id]})
    check(ids(result) == {target_id}, f'Bulk assignment failed: {path}')
    request(path, 'PUT', {'ids':[]})
    check(request(path) == [], f'Bulk clear failed: {path}')

try:
    home = request('', api=False)
    check('Forma — Access workspace' in home and '/app.js' in home, 'Welcome page is not served')
    for asset in ['styles.css','app.js','api.js']:
        check(len(request(asset,api=False)) > 500, f'Missing UI asset {asset}')
    user = create('users', {'username':f'qa_{tag}','email':f'qa_{tag}@example.com','firstName':'UI','lastName':'Tester','password':'Password@123','active':True})
    u = user['id']
    group = create('groups', {'name':f'QA {tag}','description':'UI test group'})
    g = group['id']
    role = create('roles', {'name':f'QA_{tag}','description':'UI test role'})
    r = role['id']
    permission = create('permissions', {'name':f'QA_READ_{tag}','description':'UI test permission'})
    p = permission['id']
    permission2 = create('permissions', {'name':f'QA_WRITE_{tag}','description':'Direct group permission'})
    p2 = permission2['id']
    for kind, item in [('users',user),('groups',group),('roles',role),('permissions',permission)]:
        check(request(f'{kind}/{item["id"]}')['id'] == item['id'], f'{kind} read failed')
        request(kind)
        update = {'email':f'updated_{tag}@example.com','firstName':'Updated','lastName':'Tester','active':False} if kind == 'users' else {'name':item['name'],'description':'Updated via UI workflow'}
        result = request(f'{kind}/{item["id"]}', 'PUT', update)
        check(result['active'] is False if kind == 'users' else result['description'] == 'Updated via UI workflow', 'Update failed')
    request(f'users/{u}/password', 'PUT', {'newPassword':'NewPassword@123'}, 204)
    filtered = request('users?' + urllib.parse.urlencode({'q':f'qa_{tag}','active':'false','sort':'email,asc','size':10}))
    check(u in ids(filtered['content']), 'User search/status filter failed')
    request('users?q=ab', expected=400)
    request('users?sort=password,asc', expected=400)
    request('users','POST',{'username':f'qa_{tag}','email':f'other_{tag}@example.com','firstName':'Other','lastName':'User','password':'Password@123'},409)
    request('roles','POST',{'name':'invalid lower case'},400)
    request(f'users/{u}/password','PUT',{'newPassword':'short'},400)
    request('users/9223372036854775807',expected=404)
    page = request('users/scroll?size=1')
    check(len(page['content']) == 1,'Keyset page size failed')
    if page['hasNext']:
        next_page = request(f'users/scroll?size=1&after={page["nextCursor"]}')
        check(next_page['content'][0]['id'] > page['content'][0]['id'],'Keyset cursor did not advance')
    for path, target in [(f'users/{u}/roles',r),(f'users/{u}/groups',g),(f'groups/{g}/members',u),(f'groups/{g}/roles',r),(f'groups/{g}/permissions',p),(f'roles/{r}/permissions',p)]:
        linked(path,target,paged='/members' in path)
    for path, target in [(f'users/{u}/roles',r),(f'users/{u}/groups',g),(f'groups/{g}/roles',r),(f'groups/{g}/permissions',p),(f'roles/{r}/permissions',p)]:
        replace(path,target)
    # Build a complete inheritance graph and inspect every reverse relationship.
    request(f'users/{u}/roles/{r}','PUT',expected=204)
    request(f'users/{u}/groups/{g}','PUT',expected=204)
    request(f'groups/{g}/roles/{r}','PUT',expected=204)
    request(f'roles/{r}/permissions/{p}','PUT',expected=204)
    request(f'groups/{g}/permissions/{p2}','PUT',expected=204)
    check(u in ids(request(f'roles/{r}/users')['content']), 'Reverse role users missing')
    check(g in ids(request(f'roles/{r}/groups')), 'Reverse role groups missing')
    check(r in ids(request(f'permissions/{p}/roles')), 'Reverse permission roles missing')
    check(g in ids(request(f'permissions/{p2}/groups')), 'Reverse permission groups missing')
    access = request(f'users/{u}/access')
    check(role['name'] in access['directRoles'] and role['name'] in access['groupRoles'], 'Direct/inherited roles missing')
    check(group['name'] in access['groups'], 'Access groups missing')
    check(set(access['permissions']) == {permission['name'],permission2['name']}, 'Effective permission union is wrong')
    # Deletion cascades are observable through the same read paths the UI uses.
    request(f'roles/{r}','DELETE',expected=204)
    created.remove(('roles',r))
    check(request(f'users/{u}/roles') == [], 'Deleted role left user assignments')
    check(request(f'users/{u}/access')['permissions'] == [permission2['name']], 'Access did not update after role deletion')
finally:
    for kind, item_id in reversed(created):
        request(f'{kind}/{item_id}','DELETE',expected=204)
        request(f'{kind}/{item_id}',expected=404)
print(f'PASS: {checks} live HTTP assertions; CRUD, validation, pagination, all relations, inheritance, assets, and cleanup.')
