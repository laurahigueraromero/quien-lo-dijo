package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    boolean existsByCode(String code);
}
