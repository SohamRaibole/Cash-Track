package com.cashtrack.session.repository;

import com.cashtrack.session.entity.AtmSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<AtmSession, String> {
}
