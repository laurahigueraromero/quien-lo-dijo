package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Question;
import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByRoom(Room room);

    List<Question> findByRoomAndDiscardedFalseOrderByPlayOrderAsc(Room room);

    long countByRoom(Room room);

    boolean existsByRoomAndAuthorPlayer(Room room, RoomPlayer authorPlayer);
}
