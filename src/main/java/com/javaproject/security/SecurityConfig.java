package com.javaproject.security;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
public class SecurityConfig {

    private LoggingAccessDeniedHandler accessDeniedHandler;

    @Autowired
    public void setAccessDeniedHandler(LoggingAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    @Lazy
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    /**
     * Creates a bean of type JdbcUserDetailsManager that will be used in
     * HomeController. setDataSource() alone does NOT create the underlying
     * Spring Security user/authorities tables (that used to happen via
     * auth.jdbcAuthentication().withDefaultSchema() on WebSecurityConfigurerAdapter,
     * which Spring Security 6 removed). createUserSchema() below recreates
     * those tables manually using Spring Security's default DDL.
     *
     * @return an instance configured to use our datasource
     */
    @Bean
    public JdbcUserDetailsManager jdbcUserDetailsManager() {
        JdbcUserDetailsManager jdbcUserDetailsManager = new JdbcUserDetailsManager();
        jdbcUserDetailsManager.setDataSource(dataSource);
        return jdbcUserDetailsManager;
    }

    /**
     * Replaces the deprecated WebSecurityConfigurerAdapter#configure(HttpSecurity)
     * from Spring Security 5.x. Spring Security 6 (used by Spring Boot 3.x)
     * removed WebSecurityConfigurerAdapter entirely, so authorization rules
     * are now declared as a SecurityFilterChain bean instead.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/user/**").hasAnyRole("USER", "MANAGER")
                .requestMatchers("/secured/**").hasAnyRole("USER", "MANAGER")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/", "/**").permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/secured")
            )
            .logout(logout -> logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(accessDeniedHandler)
            )
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(AbstractHttpConfigurer::disable));

        return http.build();
    }

    /**
     * Replaces the deprecated WebSecurityConfigurerAdapter#configure(AuthenticationManagerBuilder).
     * In Spring Security 6, the AuthenticationManager is obtained from the
     * shared AuthenticationConfiguration instead of overriding a base class method.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Seeds the Spring Security JDBC user/authorities tables (via createUserSchema())
     * and creates two demo users on first startup:
     *   bugs / bunny   -> ROLE_USER
     *   daffy / duck   -> ROLE_USER, ROLE_MANAGER
     * This replaces the old withDefaultSchema()...withUser(...) chain that used
     * to live inside WebSecurityConfigurerAdapter#configure(AuthenticationManagerBuilder).
     */
    @Bean
    public ApplicationRunner seedUsers(JdbcUserDetailsManager userDetailsManager) {
        return args -> {
            userDetailsManager.setEnableGroups(false);
            userDetailsManager.setEnableAuthorities(true);
            try {
                userDetailsManager.createUserSchema();
            } catch (Exception schemaAlreadyExists) {
                // schema already created on a previous run - safe to ignore
            }
            if (!userDetailsManager.userExists("bugs")) {
                userDetailsManager.createUser(
                    org.springframework.security.core.userdetails.User
                        .withUsername("bugs")
                        .password(passwordEncoder.encode("bunny"))
                        .roles("USER")
                        .build());
            }
            if (!userDetailsManager.userExists("daffy")) {
                userDetailsManager.createUser(
                    org.springframework.security.core.userdetails.User
                        .withUsername("daffy")
                        .password(passwordEncoder.encode("duck"))
                        .roles("USER", "MANAGER")
                        .build());
            }
        };
    }
}
