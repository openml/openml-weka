# Contributing

## Running the tests
The tests depend on the test.openml.org server. In the `BaseTestFramework` the api keys are defined of a couple of 
users. The `client_write_test` and `client_admin_test` do not have write / admin privileges on the test.openml.org 
server. Ideally we'd fix this by mocking the responses of the test-server.

Because this repo isn't very actively developed, I've quickly fixed this for now by giving temporary write/admin access 
to these api_keys. These commands can be used:
```sql
USE openml;
SELECT id FROM users WHERE session_hash = "[API_KEY]";
INSERT INTO users_groups (user_id, group_id) VALUES ([USER_ID_ADMIN], 1);  # admin access
INSERT INTO users_groups (user_id, group_id) VALUES ([USER_ID_WRITE], 2);  # member access
```