package org.dragon.yunpeng.samldemo.controller;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home(HttpServletRequest request, Model model, Authentication authentication) {

		String uid = request.getHeader("uid");

		if (uid == null || uid.isBlank()) {
			throw new IllegalStateException("Missing Shibboleth authenticated uid");
		}

		model.addAttribute("uid", uid);
		model.addAttribute("username", authentication.getName());
		model.addAttribute("authorities", authentication.getAuthorities());

		return "home";
	}

	@GetMapping("/user")
	@ResponseBody
	public Map<String, Object> user(HttpServletRequest request, Authentication authentication) {

		Map<String, Object> result = new LinkedHashMap<>();

		Enumeration<String> headerNames = request.getHeaderNames();

		while (headerNames != null && headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();

			List<String> values = Collections.list(request.getHeaders(headerName));

			result.put(headerName, values);
		}

		result.put("username", authentication.getName());

		return result;
	}

}
