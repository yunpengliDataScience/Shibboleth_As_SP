# LegacySpringApp — Spring Boot with legacy Spring/Security/Hibernate and XML

## Stack
- Spring Boot 2.5.15 (embedded Tomcat 9, WAR packaging, executable with `java -jar`)
- Spring Framework 5.3.37 (Maven override)
- Spring Security 5.6.1 (Maven override)
- Hibernate ORM 5.4.27.Final (explicit dependency + Maven override)
- H2 1.4.200, persistent file database
- Java 11 recommended (Boot 2.5 is a legacy release; do not assume Java 21 compatibility)
- JSP/JSTL and Spring Security JSP tags

## Build and run
```
mvn clean package
java -jar target/LegacySpringApp.war
```
Run from a writable project directory; H2 files appear under `./data/legacy-spring-db.mv.db`.
Spring listens at `127.0.0.1:9091` with context path `/LegacySpringApp`.

Public URL should be an IIS/Shibboleth-protected endpoint, e.g. `http://localhost:8088/LegacySpringApp/`.
ARR must forward `/LegacySpringApp/...` to `http://127.0.0.1:9091/LegacySpringApp/...` (preserve context path), and must not proxy `/Shibboleth.sso/...`.

## XML configuration
- `src/main/resources/applicationContext.xml`: H2 DataSource, SQL initializer, Hibernate/JPA entity manager, transaction manager, role service
- `src/main/resources/security-context.xml`: Shibboleth `uid` header filter, pre-auth provider, URL authorization rules
- `src/main/resources/application.properties`: port, loopback binding, context path, JSP resolver
- `LegacySpringAppApplication.java`: *minimal* Spring Boot launcher and XML import; no Java security configuration

## Authentication and authorization
Browser -> IIS/Shibboleth SP -> ARR -> trusted `uid` header -> Spring Security RequestHeaderAuthenticationFilter -> PreAuthenticatedAuthenticationProvider -> H2RoleUserDetailsService -> Hibernate ORM -> H2 roles -> Spring Security URL checks -> JSP.

The H2 database does **not** verify passwords. It stores usernames, enabled flags, and roles only. Unknown/disabled users or users without roles are denied. On each startup, `DataSourceInitializer` executes `db/schema.sql` and then `db/seed.sql`. The seed inserts **only missing** users and role assignments:

| uid | Roles |
|---|---|
| `yunpeng.li` | `ROLE_USER`, `ROLE_ADMIN` |
| `luan.vu` | `ROLE_USER`, `ROLE_ADMIN` |
| `jane.doe` | `ROLE_USER` |

The `uid` emitted by Shibboleth must match an H2 username. The seed uses `INSERT ... SELECT ... WHERE NOT EXISTS`, so it is safe to execute on every restart: existing enabled flags and role records are not overwritten, and duplicate roles are not inserted. Existing accounts from a previous version (such as `admin` or `regular`) are **not deleted**. Remove those manually from H2 if you want *only* the three named accounts. Existing role assignments are also retained, including any preexisting `ROLE_ADMIN` for `jane.doe`; revoke that explicitly if necessary.

To inspect the database, use an H2 SQL client with the same JDBC URL while the application is stopped, or add an authenticated admin interface. `db/seed.sql` is the startup seed source; it does not automatically remove previously stored users or roles.

### Routes
- `/LegacySpringApp/` : all authenticated users; admin links shown only to ADMIN
- `/LegacySpringApp/user` : all authenticated users
- `/LegacySpringApp/admin` : ADMIN only
- `/LegacySpringApp/admin/report` : ADMIN only
- `/LegacySpringApp/access-denied` : permission error page

### Security notes
1. **Never expose 9091 to clients.** Header-based pre-authentication is safe only when the backend can be reached solely through a trusted proxy and the proxy prevents forged `uid` headers. Shibboleth spoof checking must remain enabled.
2. Configure IIS/Shibboleth to protect all app paths and ensure that ARR passes only the Shibboleth-generated identity.
3. The app intentionally has no Spring SAML dependency. Shibboleth handles SAML; Spring handles authorization.
4. JSP link visibility is not authorization. XML rules enforce admin access server-side.
5. No login form, password validation, or logout implementation is included. Logout is controlled upstream by Shibboleth/IdP.
6. This is a compatibility demo using specifically requested old versions; assess security patches before production use.
7. Do not run two processes against the same file-based H2 DB simultaneously.

### Verify dependencies
```
mvn dependency:tree -Dincludes=org.springframework:*,org.springframework.security:*,org.hibernate:hibernate-core,org.apache.tomcat.embed:tomcat-embed-core
```
The project has not been runtime-tested in this environment. Verify with Maven and the configured IIS/Shibboleth setup.


## Displaying the authenticated username

The Home, User, Admin, Admin Report and Access Denied JSP pages include
`WEB-INF/jsp/_identity.jspf`. This fragment displays the authenticated
username with `<sec:authentication property="name" />` and shows whether
the user has `ROLE_ADMIN` via `<sec:authorize>`. It reads Spring Security's
`SecurityContext` and does not trust a user-supplied request parameter.

Sample users: `yunpeng.li` and `luan.vu` have `ROLE_ADMIN`; `jane.doe`
is a regular user. Ensure the IIS/Shibboleth reverse proxy protects and
overwrites the `uid` header and that the Spring Security servlet filter
chain is registered and active before deploying this application.
