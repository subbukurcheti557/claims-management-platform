package com.icmp.policy.policy.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icmp.policy.policy.domain.Policy;

public interface PolicyRepository extends JpaRepository<Policy, String> {

    List<Policy> findByHolderUserIdOrderByPolicyNumber(UUID holderUserId);
}
