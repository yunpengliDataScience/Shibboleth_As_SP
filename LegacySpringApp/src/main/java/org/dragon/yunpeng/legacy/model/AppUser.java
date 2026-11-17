package org.dragon.yunpeng.legacy.model;

import javax.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
	@Id
	@Column(name = "username", length = 100)
	private String username;
	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	protected AppUser() {
	}

	public String getUsername() {
		return username;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
