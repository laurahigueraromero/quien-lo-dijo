package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.GameResult;
import com.quienlodijo.backend.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    List<GameResult> findByUserOrderByPlayedAtDesc(User user);

    /**
     * Ranking global por fichas acumuladas históricas (spec.md §7, plan.md §2):
     * suma de finalFichas de cada usuario a través de todas sus partidas.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT gr.user AS user, SUM(gr.finalFichas) AS total "
                    + "FROM GameResult gr GROUP BY gr.user ORDER BY total DESC")
    List<UserFichasTotal> findGlobalRanking();

    interface UserFichasTotal {
        User getUser();

        Long getTotal();
    }
}
