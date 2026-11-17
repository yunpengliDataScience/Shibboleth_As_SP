package org.dragon.yunpeng.legacy.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
	@GetMapping("/")
	public String home(Principal principal, Model model) {
		model.addAttribute("username", principal.getName());
		return "home";
	}

	@GetMapping("/user")
	public String user(Principal principal, Model model) {
		model.addAttribute("username", principal.getName());
		return "user";
	}

	@GetMapping("/admin")
	public String admin(Principal principal, Model model) {
		model.addAttribute("username", principal.getName());
		return "admin";
	}

	@GetMapping("/admin/report")
	public String report() {
		return "report";
	}

	@GetMapping("/access-denied")
	public String denied() {
		return "access-denied";
	}
}
