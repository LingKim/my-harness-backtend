package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.AuthFailure;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

	private final AccountAuthenticationService service;

	public AccessTokenAuthenticationFilter(AccountAuthenticationService service) {
		this.service = service;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String rawAccessToken = cookie(request, AuthCookies.ACCESS);
		if (rawAccessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				AccountView account = service.currentAccount(rawAccessToken);
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(account, null, List.of()));
			} catch (AuthFailure ignored) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	private String cookie(HttpServletRequest request, String name) {
		return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
				.filter(cookie -> name.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}
}
