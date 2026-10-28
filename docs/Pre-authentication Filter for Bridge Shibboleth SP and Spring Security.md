For your architecture, I recommend a small **pre-authentication filter**: Shibboleth authenticates the user, ARR forwards the trusted `uid` header, and Spring Security converts that header into a normal Spring `Authentication`.

```
Keycloak IdP
     ↓ SAML
Shibboleth SP
     ↓
IIS / ARR
     ↓ uid: yunpeng.li
Spring Security
     ↓
Authentication
  principal = yunpeng.li
     ↓
Controller / @PreAuthorize
```

### 1. Maven dependency

You need normal Spring Security, **not** the SAML2 service-provider dependency:

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

Do **not** add this for this architecture:

```
<!-- NOT needed -->
<artifactId>spring-security-saml2-service-provider</artifactId>
```

### 2. Create a Shibboleth authentication filter

This filter reads the trusted `uid` header and creates the Spring Security `Authentication`:

```
package org.dragon.yunpeng.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ShibbolethAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String uid = request.getHeader("uid");

        if (uid != null
                && !uid.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER")
            );

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            uid,
                            null,
                            authorities);

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
```

Now:

```
HTTP header
uid: yunpeng.li
       ↓
ShibbolethAuthenticationFilter
       ↓
Spring Security Authentication
       ↓
principal = yunpeng.li
authority = ROLE_USER
```

### 3. Configure Spring Security

```
package org.dragon.yunpeng.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ShibbolethAuthenticationFilter shibbolethAuthenticationFilter;

    public SecurityConfig(
            ShibbolethAuthenticationFilter shibbolethAuthenticationFilter) {

        this.shibbolethAuthenticationFilter =
                shibbolethAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                shibbolethAuthenticationFilter,
                AnonymousAuthenticationFilter.class
            );

        return http.build();
    }
}
```

Notice there is no:

```
.formLogin(...)
```

and no:

```
.saml2Login(...)
```

because Spring isn't performing login. **Shibboleth already did it.**

### 4. Access the authenticated username

Once the filter creates the authentication, you no longer need controllers to do this everywhere:

```
request.getHeader("uid");
```

Instead:

```
@GetMapping("/")
public String home(Authentication authentication, Model model) {

    String username = authentication.getName();

    model.addAttribute("username", username);

    return "home";
}
```

For your user this would return:

```
authentication.getName()
        ↓
yunpeng.li
```

You can also use:

```
@GetMapping("/user")
@ResponseBody
public String user(Principal principal) {
    return principal.getName();
}
```

Now Spring's standard security APIs work.

### 5. Handling roles properly

My first filter hard-coded:

```
ROLE_USER
```

only to illustrate the mechanism. I would **not** do that in the real application.

For application-specific authorization, let Shibboleth provide identity:

```
uid = yunpeng.li
```

then have Spring obtain the user's roles from your application database:

```
Shibboleth
     ↓
uid = yunpeng.li
     ↓
Spring
     ↓
UserService
     ↓
Database
     ↓
yunpeng.li
   ├── ROLE_USER
   ├── ROLE_MANAGER
   └── ROLE_ADMIN
```

For example:

```
List<SimpleGrantedAuthority> authorities =
        userService.getRoles(uid)
            .stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();

var authentication =
        new UsernamePasswordAuthenticationToken(
                uid,
                null,
                authorities
        );
```

Then Spring authorization becomes very natural:

```
@GetMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public String admin() {
    return "admin";
}
```

or:

```
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public void approveRequest() {
    // ...
}
```

### 6. `application.yml`

Keep Spring inaccessible directly from other machines:

```
server:
  address: 127.0.0.1
  port: 9091

logging:
  level:
    org.springframework.security: INFO
```

This part is **critical** because Spring is trusting:

```
uid: yunpeng.li
```

If an attacker could connect directly to port `9091`, they could potentially try:

```
uid: administrator
```

and bypass Shibboleth.

Your security boundary therefore needs to remain:

```
                   PUBLIC
                     │
                     ▼
             IIS + Shibboleth
                     │
                authentication
                     │
             trusted uid header
                     │
                     ▼
               IIS / ARR
                     │
─────────────────────┼──────────────────
          TRUSTED INTERNAL BOUNDARY
                     │
                     ▼
              Spring :9091
              127.0.0.1 only
                     │
                     ▼
             Spring Security
```

One additional improvement I'd make for production is to use Spring Security's **pre-authentication support** rather than maintaining a completely custom authentication mechanism. The architecture remains the same—Shibboleth authenticates, Spring trusts the upstream identity—but Spring's `PreAuthenticatedAuthenticationProvider` can handle the integration more formally. For your current demo, however, the filter above is much easier to understand and demonstrates exactly how the pieces fit together.



--------------

If you want to use **Spring Security for authorization**, then yes, you generally want the authenticated Shibboleth user represented in Spring's `SecurityContext`.

But if your application only needs to know the username, you don't have to use Spring Security at all.

### Without Spring Security

You can simply do:

```
@GetMapping("/")
public String home(HttpServletRequest request, Model model) {

    String uid = request.getHeader("uid");

    model.addAttribute("username", uid);

    return "home";
}
```

Architecture:

```
Shibboleth
    ↓
uid: yunpeng.li
    ↓
Spring Controller
    ↓
request.getHeader("uid")
```

That's perfectly reasonable for a simple application.

### With Spring Security

If you want features such as:

```
@PreAuthorize("hasRole('ADMIN')")
```

or:

```
authentication.getName()
```

or:

```
request.isUserInRole("ADMIN")
```

then Spring Security needs an `Authentication` associated with the current request.

Conceptually:

```
uid header
   ↓
Spring Security filter
   ↓
Authentication
   ↓
SecurityContext
   ↓
Controller / @PreAuthorize
```

In the example I gave earlier, this part does that:

```
var authentication =
    new UsernamePasswordAuthenticationToken(
        uid,
        null,
        authorities);

SecurityContextHolder.getContext()
        .setAuthentication(authentication);
```

However, I would **not build your final implementation around manually calling `SecurityContextHolder` like this**. It works as a demonstration, but Spring Security has a cleaner model for your exact situation: **pre-authenticated authentication**.

Your situation is precisely:

> An external system (Shibboleth) has already authenticated the user, and Spring Security should trust that identity and perform authorization.

So the production design I'd recommend is:

```
Keycloak
   ↓
Shibboleth SP
   ↓
authenticated uid
   ↓
IIS / ARR
   ↓
Spring Security
   ↓
Pre-authenticated user
   ↓
SecurityContext managed by Spring Security
   ↓
Authorization
```

Spring Security still uses a `SecurityContext` internally—that's fundamental to how Spring Security knows the current authenticated principal—but **your business code doesn't need to manipulate `SecurityContextHolder` itself**.

Your controller can simply use:

```
@GetMapping("/")
public String home(Authentication authentication, Model model) {

    model.addAttribute(
        "username",
        authentication.getName());

    return "home";
}
```

So the distinction is:

| Requirement                     | SecurityContext needed? |
| ------------------------------- | ----------------------- |
| Just `request.getHeader("uid")` | ❌ No                    |
| Display authenticated username  | ❌ Not necessarily       |
| Use Spring Security             | ✅ Yes, internally       |
| `Authentication.getName()`      | ✅ Yes                   |
| `@PreAuthorize`                 | ✅ Yes                   |
| `hasRole("ADMIN")`              | ✅ Yes                   |
| Method-level authorization      | ✅ Yes                   |

For **your architecture**, if you want Spring to handle application security/authorization, I recommend using Spring Security's **pre-authentication mechanism** and letting Spring manage the `SecurityContext` rather than manually setting it.

If you'd like, I can give you the **proper `PreAuthenticatedAuthenticationProvider` configuration for Shibboleth + `uid` header**, replacing the simplified filter I gave above.



-----------

No. For your **final/production Spring Security configuration**, I would **not manually create a `UsernamePasswordAuthenticationToken` and put it into `SecurityContextHolder`**.

That approach works technically, but it bypasses much of Spring Security's normal authentication architecture.

Your situation is specifically **pre-authentication**:

```
Keycloak
   ↓
Shibboleth SP       ← authentication happens here
   ↓
uid: yunpeng.li
   ↓
IIS / ARR
   ↓
Spring Security     ← trusts pre-authenticated identity
   ↓
Authorization
```

Spring Security has classes designed for exactly this.

### Recommended approach

Use a `RequestHeaderAuthenticationFilter` to extract the Shibboleth header:

```
@Bean
RequestHeaderAuthenticationFilter shibbolethAuthenticationFilter(
        AuthenticationManager authenticationManager) {

    RequestHeaderAuthenticationFilter filter =
            new RequestHeaderAuthenticationFilter();

    filter.setPrincipalRequestHeader("uid");
    filter.setExceptionIfHeaderMissing(true);
    filter.setAuthenticationManager(authenticationManager);

    return filter;
}
```

Then use a `PreAuthenticatedAuthenticationProvider`:

```
@Bean
PreAuthenticatedAuthenticationProvider preAuthProvider(
        UserDetailsService userDetailsService) {

    var provider = new PreAuthenticatedAuthenticationProvider();

    provider.setPreAuthenticatedUserDetailsService(
        new UserDetailsByNameServiceWrapper<>(userDetailsService)
    );

    return provider;
}
```

Your `UserDetailsService` can load application roles:

```
@Bean
UserDetailsService userDetailsService() {

    return username -> User
            .withUsername(username)
            .password("")
            .roles("USER")
            .build();
}
```

Then configure the chain:

```
@Bean
SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        RequestHeaderAuthenticationFilter shibbolethAuthenticationFilter)
        throws Exception {

    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .addFilterBefore(
            shibbolethAuthenticationFilter,
            AnonymousAuthenticationFilter.class
        );

    return http.build();
}
```

Spring Security then handles the rest:

```
uid header
   ↓
RequestHeaderAuthenticationFilter
   ↓
PreAuthenticatedAuthenticationToken
   ↓
AuthenticationManager
   ↓
PreAuthenticatedAuthenticationProvider
   ↓
UserDetailsService
   ↓
roles / authorities
   ↓
SecurityContext
   ↓
Controller
```

Your application code can simply do:

```
@GetMapping("/")
public String home(Authentication authentication) {

    String username = authentication.getName();

    return "home";
}
```

Or authorization works normally:

```
@PreAuthorize("hasRole('ADMIN')")
public void adminFunction() {
}
```

### So don't do this yourself

```
var authentication =
    new UsernamePasswordAuthenticationToken(
        uid,
        null,
        authorities);

SecurityContextHolder.getContext()
    .setAuthentication(authentication);
```

Let Spring Security create and manage the authenticated security context.

Also, `UsernamePasswordAuthenticationToken` is conceptually misleading here because **Spring never received or verified a username/password**. Shibboleth already authenticated the user. `PreAuthenticatedAuthenticationToken` accurately represents what happened.

For your architecture, the clean model is:

> **Shibboleth authenticates → Spring Security accepts a pre-authenticated identity → Spring loads roles → Spring authorizes.**

And the `uid` header must only be trusted because your Spring port is inaccessible directly and requests can reach it only through the trusted IIS/Shibboleth/ARR path.
