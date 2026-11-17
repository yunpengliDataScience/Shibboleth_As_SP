package org.dragon.yunpeng.legacy.model;

import javax.persistence.*;

@Entity
@Table(name = "app_roles")
public class AppRole {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "username", nullable = false)
	private String username;
	
	@Column(name = "authority", nullable = false)
	private String authority;

	protected AppRole() {
	}

	public String getAuthority() {
		return authority;
	}
}
