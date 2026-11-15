-- Database-per-service: each service gets its own database and its own login, and cannot read the others.
-- (One PostgreSQL instance locally to save memory; in production each would be a separate instance.)

CREATE USER identity_user WITH PASSWORD 'identity_pass';
CREATE DATABASE identity_db OWNER identity_user;

CREATE USER policy_user WITH PASSWORD 'policy_pass';
CREATE DATABASE policy_db OWNER policy_user;

CREATE USER claims_user WITH PASSWORD 'claims_pass';
CREATE DATABASE claims_db OWNER claims_user;

CREATE USER notification_user WITH PASSWORD 'notification_pass';
CREATE DATABASE notification_db OWNER notification_user;

REVOKE CONNECT ON DATABASE identity_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE policy_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE claims_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;

GRANT CONNECT ON DATABASE identity_db TO identity_user;
GRANT CONNECT ON DATABASE policy_db TO policy_user;
GRANT CONNECT ON DATABASE claims_db TO claims_user;
GRANT CONNECT ON DATABASE notification_db TO notification_user;
