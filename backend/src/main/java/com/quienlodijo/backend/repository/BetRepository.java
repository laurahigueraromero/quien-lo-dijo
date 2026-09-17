package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Bet;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.Round;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findByRound(Round round);

    boolean existsByRoundAndBettor(Round round, RoomPlayer bettor);
}
