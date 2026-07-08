package com.securevoting.repository;

import com.securevoting.entity.ReceiptIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptIndexRepository extends JpaRepository<ReceiptIndex, Long> {

    Optional<ReceiptIndex> findByReceiptHash(String receiptHash);
}