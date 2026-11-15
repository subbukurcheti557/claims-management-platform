package com.icmp.identity.config;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.identity.user.domain.AppUser;
import com.icmp.identity.user.domain.Role;
import com.icmp.identity.user.infrastructure.UserRepository;

/**
 * Seeds demo users with fixed ids. The ids are shared with the policy and claims seed data, which is how the
 * three independent databases line up for the demo. Runs only when {@code app.seed-demo-users=true}.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.demo-password:Password1!}") String demoPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Seed> seeds = List.of(
                new Seed("11111111-1111-1111-1111-111111111111", "alice@claimant.test", "Alice Tan", Role.CLAIMANT, "SG"),
                new Seed("22222222-2222-2222-2222-222222222222", "bob@claimant.test", "Bob Wong", Role.CLAIMANT, "HK"),
                new Seed("66666666-6666-6666-6666-666666666666", "carol@claimant.test", "Carol Smith", Role.CLAIMANT, "AU"),
                new Seed("77777777-7777-7777-7777-777777777777", "dev@claimant.test", "Dev Kumar", Role.CLAIMANT, "MY"),
                new Seed("88888888-8888-8888-8888-888888888888", "tara@claimant.test", "Tara Suksan", Role.CLAIMANT, "TH"),
                new Seed("99999999-9999-9999-9999-999999999999", "indra@claimant.test", "Indra Wijaya", Role.CLAIMANT, "ID"),
                new Seed("33333333-3333-3333-3333-333333333333", "olivia@staff.test", "Olivia Lim", Role.CLAIMS_OFFICER, "SG"),
                new Seed("44444444-4444-4444-4444-444444444444", "omar@staff.test", "Omar Rahman", Role.CLAIMS_OFFICER, "MY"),
                new Seed("55555555-5555-5555-5555-555555555555", "maya@staff.test", "Maya Chen", Role.CLAIMS_MANAGER, "SG"));
        String hash = passwordEncoder.encode(demoPassword);
        int created = 0;
        for (Seed s : seeds) {
            UUID id = UUID.fromString(s.id());
            if (!users.existsById(id)) {
                users.save(new AppUser(id, s.email(), hash, s.name(), s.role(), s.market()));
                created++;
            }
        }
        log.info("Demo user seeding complete: {} created, {} already present", created, seeds.size() - created);
    }

    private record Seed(String id, String email, String name, Role role, String market) {
    }
}
