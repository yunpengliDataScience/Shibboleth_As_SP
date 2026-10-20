package org.dragon.yunpeng.samldemo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

	/*
	 * Returns the Thymeleaf template:
	 *
	 * src/main/resources/templates/home.html
	 *
	 * Do NOT add @ResponseBody here. Otherwise Spring will return the literal text
	 * "home".
	 */
	@GetMapping("/")
	public String home() {
		return "home";
	}

	/*
	 * Because this method returns JSON instead of a template, we add @ResponseBody.
	 */
	@GetMapping("/private")
	@ResponseBody
	public Map<String, Object> privatePage(Authentication authentication) {

		Map<String, Object> result = new LinkedHashMap<>();

		result.put("message", "SAML authentication succeeded.");

		result.put("name", authentication.getName());

		result.put("authenticationType", authentication.getClass().getName());

		result.put("authorities", authentication.getAuthorities());

		return result;
	}

	/*
	 * Return SAML user information as JSON.
	 */
	@GetMapping("/user")
	@ResponseBody
	public Map<String, Object> user(Authentication authentication) {

		Map<String, Object> result = new LinkedHashMap<>();

		if (!(authentication instanceof Saml2Authentication samlAuthentication)) {

			result.put("message", "Current authentication is not a Saml2Authentication.");

			return result;
		}

		Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) samlAuthentication.getPrincipal();

		result.put("name", principal.getName());

		result.put("authorities", samlAuthentication.getAuthorities());

		result.put("attributes", principal.getAttributes());

		result.put("sessionIndexes", principal.getSessionIndexes());

		/*
		 * These values will exist only if Keycloak SAML mappers send attributes with
		 * these names.
		 */
		result.put("email", principal.getFirstAttribute("email"));

		result.put("firstName", principal.getFirstAttribute("firstName"));

		result.put("lastName", principal.getFirstAttribute("lastName"));

		return result;
	}
}