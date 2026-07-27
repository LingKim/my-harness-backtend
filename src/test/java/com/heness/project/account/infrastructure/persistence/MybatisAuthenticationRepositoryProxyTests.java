package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.AuthenticationRepository;
import com.heness.project.account.application.LoginFailureStore;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisAuthenticationRepositoryProxyTests {

	@Test
	void supportsSpringClassBasedProxying() {
		MybatisAuthenticationRepository repository =
				new MybatisAuthenticationRepository(null, null, null);
		ProxyFactory proxyFactory = new ProxyFactory(repository);
		proxyFactory.setProxyTargetClass(true);

		assertThat(proxyFactory.getProxy()).isInstanceOf(AuthenticationRepository.class);
	}

	@Test
	void loginFailureStoreSupportsSpringClassBasedProxying() {
		MybatisLoginFailureStore store = new MybatisLoginFailureStore(null);
		ProxyFactory proxyFactory = new ProxyFactory(store);
		proxyFactory.setProxyTargetClass(true);

		assertThat(proxyFactory.getProxy()).isInstanceOf(LoginFailureStore.class);
	}
}
