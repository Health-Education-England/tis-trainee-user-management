/*
 * The MIT License (MIT)
 *
 * Copyright 2022 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.usermanagement.config;

import com.transformuk.hee.tis.profile.client.config.JwtSpringSecurityConfig;
import com.transformuk.hee.tis.security.JwtAuthenticationEntryPoint;
import com.transformuk.hee.tis.security.JwtAuthenticationProvider;
import com.transformuk.hee.tis.security.JwtAuthenticationSuccessHandler;
import com.transformuk.hee.tis.security.RestAccessDeniedHandler;
import com.transformuk.hee.tis.security.filter.JwtAuthenticationTokenFilter;
import java.util.Collections;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableAutoConfiguration
@Import(JwtSpringSecurityConfig.class)
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final JwtAuthenticationEntryPoint unauthorizedHandler;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final JwtAuthenticationProvider authenticationProvider;

  SecurityConfig(JwtAuthenticationEntryPoint unauthorizedHandler,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationProvider authenticationProvider) {
    this.unauthorizedHandler = unauthorizedHandler;
    this.accessDeniedHandler = accessDeniedHandler;
    this.authenticationProvider = authenticationProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager() {
    return new ProviderManager(Collections.singletonList(authenticationProvider));
  }

  @Bean
  public JwtAuthenticationTokenFilter authenticationTokenFilterBean() {
    JwtAuthenticationTokenFilter authenticationTokenFilter = new JwtAuthenticationTokenFilter(
        "/api/**");
    authenticationTokenFilter.setAuthenticationManager(authenticationManager());
    authenticationTokenFilter
        .setAuthenticationSuccessHandler(new JwtAuthenticationSuccessHandler());
    return authenticationTokenFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // we don't need CSRF because our token is invulnerable
        .csrf().disable()
        .authorizeRequests()
        .antMatchers(HttpMethod.GET, "/api/**").hasAuthority("trainee-support:view")
        .antMatchers(HttpMethod.POST, "/api/**").hasAuthority("trainee-support:modify")
        .anyRequest().authenticated()
        .and()
        .exceptionHandling().authenticationEntryPoint(unauthorizedHandler)
        .accessDeniedHandler(accessDeniedHandler)
        .and()
        // don't create session
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    // Custom JWT based security filter
    http.addFilterBefore(authenticationTokenFilterBean(),
        UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
