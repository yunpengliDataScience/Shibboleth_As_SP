package org.dragon.yunpeng.legacy.security;

import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.dragon.yunpeng.legacy.model.AppUser;
import org.dragon.yunpeng.legacy.model.AppRole;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

public class MyUserDetailsService implements UserDetailsService {

	@PersistenceContext
	private EntityManager em;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		// Find the user by username from database.
		AppUser user = em.find(AppUser.class, username);

		if (user == null || !user.isEnabled())
			throw new UsernameNotFoundException("Unknown or disabled user: " + username);

		// Retrieve roles of the user in database.
		List<AppRole> roles = em.createQuery("select r from AppRole r where r.username=:username", AppRole.class)
				.setParameter("username", username).getResultList();

		if (roles.isEmpty())
			throw new UsernameNotFoundException("No roles assigned: " + username);

		// Construct a UserDetails, which has username and authorities.
		return new User(user.getUsername(), "N/A", user.isEnabled(), true, true, true,
				roles.stream().map(r -> new SimpleGrantedAuthority(r.getAuthority())).collect(Collectors.toList()));
	}
}
