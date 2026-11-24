package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query(value = "SELECT resolve_customer(:name, :address, :phone)", nativeQuery = true)
    Long resolve(@Param("name") String name, @Param("address") String address, @Param("phone") String phone);
}
