package org.dragon.yunpeng.samldemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	UserDetailsService userDetailsService() {
		return username -> {
			// if ("admin".equalsIgnoreCase(username))
			// return User.withUsername(username).password("").roles("USER",
			// "ADMIN").build();
			return User.withUsername(username).password("").roles("USER").build();
		};
	}

	// Already authenticated before reaching Spring
	@Bean
	PreAuthenticatedAuthenticationProvider preAuthProvider(UserDetailsService uds) {

		var wrapper = new UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken>();

		wrapper.setUserDetailsService(uds);

		var provider = new PreAuthenticatedAuthenticationProvider();
		provider.setPreAuthenticatedUserDetailsService(wrapper);

		return provider;
	}

	@Bean
	AuthenticationManager authenticationManager(PreAuthenticatedAuthenticationProvider provider) {
		return new ProviderManager(provider);
	}

	@Bean
	RequestHeaderAuthenticationFilter shibbolethAuthenticationFilter(AuthenticationManager manager) {

		RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();

		filter.setPrincipalRequestHeader("uid");
		filter.setExceptionIfHeaderMissing(true);
		filter.setAuthenticationManager(manager);

		return filter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, RequestHeaderAuthenticationFilter filter)
			throws Exception {

		http.authorizeHttpRequests(
				auth -> auth.requestMatchers("/css/**", "/js/**", "/images/**").permitAll().anyRequest().authenticated()
		// .requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().authenticated()
		).addFilterBefore(filter, AnonymousAuthenticationFilter.class)
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new Http403ForbiddenEntryPoint()));

		return http.build();
	}
}
