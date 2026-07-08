package com.securevoting.repository;

import com.securevoting.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findTopByOrderByBlockIndexDesc();

    Optional<Block> findByBlockIndex(long blockIndex);

    Optional<Block> findByHash(String hash);

    List<Block> findAllByOrderByBlockIndexAsc();

    long countByBlockIndexGreaterThanEqual(long minIndex);
}