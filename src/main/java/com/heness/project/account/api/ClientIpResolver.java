package com.heness.project.account.api;

import com.heness.project.account.infrastructure.security.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class ClientIpResolver {

	private final AuthProperties properties;

	ClientIpResolver(AuthProperties properties) {
		this.properties = properties;
	}

	String resolve(HttpServletRequest request) {
		String remoteAddress = canonical(request.getRemoteAddr());
		if (!isTrustedProxy(remoteAddress)) {
			return remoteAddress;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded == null || forwarded.isBlank()) {
			return remoteAddress;
		}
		String first = forwarded.split(",", 2)[0].trim();
		try {
			return canonical(first);
		} catch (IllegalArgumentException invalid) {
			return remoteAddress;
		}
	}

	private boolean isTrustedProxy(String remoteAddress) {
		return properties.trustedProxyCidrs().stream().anyMatch(cidr -> contains(cidr, remoteAddress));
	}

	private boolean contains(String cidr, String address) {
		try {
			String[] parts = cidr.split("/", 2);
			byte[] network = InetAddress.getByName(parts[0]).getAddress();
			byte[] candidate = InetAddress.getByName(address).getAddress();
			if (network.length != candidate.length) {
				return false;
			}
			int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
			if (prefix < 0 || prefix > network.length * 8) {
				return false;
			}
			for (int bit = 0; bit < prefix; bit++) {
				int mask = 1 << (7 - bit % 8);
				if ((network[bit / 8] & mask) != (candidate[bit / 8] & mask)) {
					return false;
				}
			}
			return true;
		} catch (UnknownHostException | NumberFormatException invalid) {
			return false;
		}
	}

	private String canonical(String address) {
		try {
			return InetAddress.getByName(address).getHostAddress();
		} catch (UnknownHostException invalid) {
			throw new IllegalArgumentException("客户端地址无效");
		}
	}
}
