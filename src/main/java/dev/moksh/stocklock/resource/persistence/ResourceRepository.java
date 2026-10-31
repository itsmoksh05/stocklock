package dev.moksh.stocklock.resource.persistence;

import dev.moksh.stocklock.resource.domain.Resource;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
}
