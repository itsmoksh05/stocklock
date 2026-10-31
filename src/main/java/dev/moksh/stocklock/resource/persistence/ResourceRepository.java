package dev.moksh.stocklock.resource.persistence;

import dev.moksh.stocklock.resource.domain.Resource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Resource resource set resource.quantity = resource.quantity - :quantity "
			+ "where resource.id = :resourceId and resource.quantity >= :quantity")
	int decrementAvailableQuantity(@Param("resourceId") Long resourceId, @Param("quantity") int quantity);
}
