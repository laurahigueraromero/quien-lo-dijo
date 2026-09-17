package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.Round;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByRoom(Room room);

    /** La ronda más reciente de la sala, si existe (para saber cuál está activa ahora mismo). */
    Optional<Round> findTopByRoomOrderByIdDesc(Room room);
}
