package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {

    List<RoomPlayer> findByRoom(Room room);

    Optional<RoomPlayer> findByRoomAndUser(Room room, User user);

    boolean existsByRoomAndUser(Room room, User user);
}
