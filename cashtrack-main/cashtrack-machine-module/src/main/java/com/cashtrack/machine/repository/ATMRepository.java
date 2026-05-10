package com.cashtrack.machine.repository;

import com.cashtrack.machine.entity.ATMMachine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ATMRepository extends JpaRepository<ATMMachine, String> {
}