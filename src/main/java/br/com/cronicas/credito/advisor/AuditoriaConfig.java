package br.com.cronicas.credito.advisor;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies the audit advisor to every {@code ChatClient} in the application.
 *
 * <p>Before this existed, each service wired the advisor itself — which meant the
 * negotiation assistant was audited and the classifier and the policy query were
 * not. Nobody decided that; it is simply what happens when a cross-cutting
 * concern is left to be remembered at each call site. The next service added to
 * this codebase would have had the same coin-flip.
 *
 * <p>{@code ChatClient.Builder} is a prototype bean, and Spring AI's
 * autoconfiguration passes every {@link ChatClientBuilderCustomizer} bean over
 * each new instance before handing it out. So a single bean here is the
 * difference between "audited by convention" and "audited by construction", and
 * a service can no longer opt out of the audit trail by forgetting about it.
 */
@Configuration(proxyBeanMethods = false)
public class AuditoriaConfig {

    @Bean
    ChatClientBuilderCustomizer auditTrailCustomizer(AuditTrailAdvisor auditTrailAdvisor) {
        return builder -> builder.defaultAdvisors(auditTrailAdvisor);
    }
}
