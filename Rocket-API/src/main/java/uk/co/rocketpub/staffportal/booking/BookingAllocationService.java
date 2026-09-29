package uk.co.rocketpub.staffportal.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingAllocationService {
    private final JdbcTemplate jdbc;

    public BookingAllocationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> suggest(LocalDate date, LocalTime time, int partySize, boolean eatingFood,
            Long preferredAreaId, Long preferredTableId, boolean wantsNearTv, boolean avoidsBench,
            Long ignoredBookingId, List<Long> excludedTableIds) {
        List<TableOption> tables = jdbc.query("""
            SELECT pt.id,pt.capacity,pt.area_id,COALESCE(pt.near_tv,0),COALESCE(pt.has_bench,0),
              COALESCE(pt.unsuitable_for_food,0)
            FROM pub_table pt JOIN area a ON a.id=pt.area_id
            WHERE pt.active=1 AND lower(a.name)!='bar'
            """, (rs, row) -> new TableOption(rs.getLong(1), rs.getInt(2), rs.getLong(3),
                    rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6)));
        List<Long> blocked = new ArrayList<>(excludedTableIds == null ? List.of() : excludedTableIds);
        blocked.addAll(jdbc.query("""
            SELECT DISTINCT bt.table_id FROM booking_table bt JOIN booking b ON b.id=bt.booking_id
            WHERE b.booking_date=? AND b.status NOT IN ('Cancelled','Completed')
              AND time(b.booking_time)<time(?,'+150 minutes')
              AND time(b.booking_time,'+'||b.duration_minutes||' minutes')>time(?)
              AND (? IS NULL OR b.id!=?)
            """, (rs, row) -> rs.getLong(1), date.toString(), time.toString(), time.toString(), ignoredBookingId, ignoredBookingId));
        List<TableOption> availableTables = tables.stream().filter(table -> !blocked.contains(table.id())).toList();

        if (preferredTableId != null) {
            TableOption preferred = availableTables.stream().filter(table -> table.id() == preferredTableId).findFirst().orElse(null);
            if (preferred != null && preferred.capacity() >= partySize && suitable(preferred, eatingFood, wantsNearTv, avoidsBench)) {
                return List.of(preferred.id());
            }
        }

        TableOption single = availableTables.stream()
                .filter(table -> table.capacity() >= partySize)
                .filter(table -> suitable(table, eatingFood, wantsNearTv, avoidsBench))
                .min(Comparator.comparingInt((TableOption table) -> score(table, partySize, preferredAreaId, eatingFood, wantsNearTv, avoidsBench))
                        .thenComparingLong(TableOption::id)).orElse(null);
        if (single != null) return List.of(single.id());

        List<PairOption> pairs = jdbc.query("SELECT table_a_id,table_b_id FROM table_pairing", (rs, row) -> new PairOption(rs.getLong(1), rs.getLong(2)));
        return pairs.stream().map(pair -> {
            TableOption first = availableTables.stream().filter(table -> table.id() == pair.first()).findFirst().orElse(null);
            TableOption second = availableTables.stream().filter(table -> table.id() == pair.second()).findFirst().orElse(null);
            return first == null || second == null ? null : new PairCandidate(first, second);
        }).filter(pair -> pair != null && pair.capacity() >= partySize)
          .filter(pair -> suitable(pair.first(), eatingFood, wantsNearTv, avoidsBench) && suitable(pair.second(), eatingFood, wantsNearTv, avoidsBench))
          .min(Comparator.comparingInt(pair -> pair.capacity() - partySize))
          .map(pair -> List.of(pair.first().id(), pair.second().id())).orElse(List.of());
    }

    private boolean suitable(TableOption table, boolean eating, boolean tv, boolean avoidsBench) {
        return (!eating || !table.unsuitableForFood()) && (!tv || table.nearTv()) && (!avoidsBench || !table.hasBench());
    }

    private int score(TableOption table, int people, Long area, boolean eating, boolean tv, boolean avoidsBench) {
        int score = table.capacity() - people;
        if (area != null && table.areaId() != area) score += 100;
        if (eating && table.unsuitableForFood()) score += 1000;
        if (tv && !table.nearTv()) score += 200;
        if (avoidsBench && table.hasBench()) score += 200;
        return score;
    }

    private record TableOption(long id, int capacity, long areaId, boolean nearTv, boolean hasBench, boolean unsuitableForFood) {}
    private record PairOption(long first, long second) {}
    private record PairCandidate(TableOption first, TableOption second) { int capacity() { return first.capacity() + second.capacity(); } }
}
