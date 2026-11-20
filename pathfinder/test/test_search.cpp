#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

#include "pathfinder/model.hpp"
#include "pathfinder/search.hpp"

using namespace pf;

// A tiny test helper: print the failing line and keep going.
static int failures = 0;
#define CHECK(...)                                                                   \
    do {                                                                             \
        if (!(__VA_ARGS__)) {                                                        \
            std::cerr << __FILE__ << ":" << __LINE__ << "  CHECK failed: " #__VA_ARGS__ "\n"; \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

using Path = std::vector<Cell>;

static Query make_query(const std::vector<std::string>& rows, Cell start, Cell goal, int budget) {
    std::string error;
    auto grid = parse_grid(rows, error);
    if (!grid) {
        std::cerr << "test setup error: " << error << "\n";
        std::exit(2);
    }
    Query q;
    q.grid = *grid;
    q.start = start;
    q.goal = goal;
    q.budget = budget;
    return q;
}

static const std::vector<std::string> kOpen6 = {"111111", "111111", "111111", "111111", "111111", "111111"};

// ---- parse_grid -------------------------------------------------------------

static void test_parse_grid() {
    std::string error;
    auto ok = parse_grid({"12111", "11111"}, error);
    CHECK(ok.has_value());
    CHECK(ok->width == 5 && ok->height == 2);
    CHECK(ok->multiplier(1, 0) == 2);

    CHECK(!parse_grid({}, error).has_value());                         // no rows
    CHECK(!parse_grid({""}, error).has_value());                       // empty row
    CHECK(!parse_grid({"11", "1"}, error).has_value());                // ragged rows
    CHECK(!parse_grid({"1a"}, error).has_value());                     // bad character
    CHECK(!parse_grid({std::string(201, '1')}, error).has_value());    // too wide
    CHECK(!parse_grid(std::vector<std::string>(201, "1"), error).has_value());  // too tall
    CHECK(parse_grid(std::vector<std::string>(200, std::string(200, '1')), error).has_value());
}

// ---- find_path --------------------------------------------------------------

static void test_zigzag_example() {  // spec 5.3 and 6.1, case C-02
    Query q = make_query({"12111", "11111"}, {0, 0}, {4, 0}, 600);
    PathResult r = find_path(q);
    CHECK(r.found);
    CHECK((r.path == Path{{0, 0}, {1, 1}, {2, 0}, {3, 0}, {4, 0}}));
    CHECK(r.cost == 400);
    CHECK(r.expansions == 5);
}

static void test_start_equals_goal() {  // case C-03
    Query q = make_query({"01"}, {0, 0}, {0, 0}, 0);  // the start is even a wall
    PathResult r = find_path(q);
    CHECK(r.found && r.cost == 0 && r.path == Path{{0, 0}});

    q.no_stop = {{0, 0}};
    r = find_path(q);
    CHECK(r.found && r.cost == 0 && r.path == Path{{0, 0}});
    CHECK(r.expansions == 1);  // the spec does not say; this is the literal reading of 5.3
}

static void test_goal_blocked_and_no_stop() {  // cases C-04, C-05
    Query q = make_query({"101"}, {0, 0}, {1, 0}, 1000);
    CHECK(find_path(q).reason == Reason::GoalBlocked);

    q = make_query({"111"}, {0, 0}, {2, 0}, 1000);
    q.no_stop = {{2, 0}};
    PathResult r = find_path(q);
    CHECK(!r.found && r.reason == Reason::GoalNoStop);
}

static void test_pass_through_no_stop() {  // case C-06
    Query q = make_query({"111"}, {0, 0}, {2, 0}, 600);
    q.no_stop = {{1, 0}};
    PathResult r = find_path(q);
    CHECK(r.found && r.cost == 200);
    CHECK((r.path == Path{{0, 0}, {1, 0}, {2, 0}}));
}

static void test_budget_edges() {  // case C-09
    Query q = make_query({"111"}, {0, 0}, {2, 0}, 200);
    CHECK(find_path(q).found);
    q.budget = 199;
    PathResult r = find_path(q);
    CHECK(!r.found && r.reason == Reason::NoPathWithinBudget);
}

static void test_enclosed_goal() {  // case C-08
    Query q = make_query({"11111", "10001", "10101", "10001", "11111"}, {0, 0}, {2, 2}, 100000);
    PathResult r = find_path(q);
    CHECK(!r.found && r.reason == Reason::NoPathWithinBudget);
}

static void test_corner_cutting() {  // case C-10
    Query q = make_query({"10", "11"}, {0, 0}, {1, 1}, 1000);
    PathResult forbid = find_path(q);
    CHECK(forbid.found && forbid.cost == 200);
    CHECK((forbid.path == Path{{0, 0}, {0, 1}, {1, 1}}));

    q.corners = Corners::Allow;
    PathResult allow = find_path(q);
    CHECK(allow.found && allow.cost == 100);
    CHECK((allow.path == Path{{0, 0}, {1, 1}}));
}

static void test_true_diagonal() {  // spec 4.3.1, case C-11
    Query q = make_query(kOpen6, {0, 0}, {4, 4}, 600);
    q.metric = Metric::TrueDiagonal;
    PathResult r = find_path(q);
    CHECK(r.found && r.cost == 564);

    q.goal = {5, 5};
    CHECK(!find_path(q).found);  // five diagonals cost 705
}

static void test_alternating() {  // spec 4.3.1, case C-12
    Query q = make_query(kOpen6, {0, 0}, {4, 4}, 600);
    q.metric = Metric::Alternating;
    for (int parity : {0, 1}) {
        q.parity = parity;
        PathResult r = find_path(q);
        CHECK(r.found && r.cost == 600);
    }
    q.goal = {1, 1};
    q.parity = 0;
    CHECK(find_path(q).cost == 100);  // the first diagonal is cheap at parity 0
    q.parity = 1;
    CHECK(find_path(q).cost == 200);  // and expensive at parity 1
}

static void test_multipliers() {  // case C-13
    Query q = make_query({"1349"}, {0, 0}, {3, 0}, 2000);
    PathResult r = find_path(q);
    CHECK(r.found && r.cost == 300 + 400 + 900);

    q = make_query({"10", "03"}, {0, 0}, {1, 1}, 1000);  // only a diagonal move is possible
    q.metric = Metric::TrueDiagonal;
    q.corners = Corners::Allow;
    r = find_path(q);
    CHECK(r.found && r.cost == 423);  // 141 x 3
}

static void test_tie_breaking() {  // case C-14, expected values from an independent reference
    const Path expected = {{0, 0}, {1, 0}, {2, 0}, {3, 1}, {4, 2}};
    const std::vector<std::string> open5(5, "11111");

    Query q = make_query(open5, {0, 0}, {4, 2}, 10000);
    PathResult r = find_path(q);
    CHECK(r.path == expected && r.cost == 400 && r.expansions == 5);

    q.metric = Metric::TrueDiagonal;
    r = find_path(q);
    CHECK(r.path == expected && r.cost == 482 && r.expansions == 7);

    q.metric = Metric::Alternating;
    r = find_path(q);
    CHECK(r.path == expected && r.cost == 500 && r.expansions == 7);
    q.parity = 1;
    r = find_path(q);
    CHECK(r.path == expected && r.cost == 500 && r.expansions == 5);
}

static void test_expansion_cap() {  // case C-19
    Query q = make_query(kOpen6, {0, 0}, {5, 5}, 100000);
    PathResult r = find_path(q, 2);
    CHECK(!r.found && r.reason == Reason::ExpansionCap && r.expansions == 2);
}

static void test_big_unreachable_goal() {  // case C-17
    std::vector<std::string> rows(200, std::string(200, '1'));
    rows[198][198] = '0';
    rows[198][199] = '0';
    rows[199][198] = '0';  // walls around the corner goal at (199,199)
    Query q = make_query(rows, {0, 0}, {199, 199}, 10000000);

    const auto begin = std::chrono::steady_clock::now();
    PathResult r = find_path(q);
    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - begin).count();

    CHECK(!r.found && r.reason == Reason::NoPathWithinBudget);
    CHECK(r.expansions == 200 * 200 - 4);  // every reachable cell, never the goal
    std::cout << "  200x200 unreachable goal took " << ms << " ms (spec target is under 80 ms p95)\n";
}

// ---- reach ------------------------------------------------------------------

static void test_reach_example() {  // spec 5.4, case C-15
    Query q = make_query({"12111", "11111"}, {0, 0}, {0, 0}, 300);
    ReachResult r = reach(q);
    CHECK((r.costs == std::vector<int>{0, 200, 200, 300, -1, 100, 100, 200, 300, -1}));
    CHECK((r.stop == std::vector<char>{1, 1, 1, 1, 0, 1, 1, 1, 1, 0}));
}

static void test_reach_budget_zero_and_no_stop() {  // cases C-15, C-16
    Query q = make_query({"11"}, {0, 0}, {0, 0}, 0);
    ReachResult r = reach(q);
    CHECK((r.costs == std::vector<int>{0, -1}));
    CHECK((r.stop == std::vector<char>{1, 0}));

    q = make_query({"011"}, {0, 0}, {0, 0}, 300);  // the start is a wall: the creature can still leave
    q.no_stop = {{1, 0}};
    r = reach(q);
    CHECK((r.costs == std::vector<int>{0, 100, 200}));
    CHECK((r.stop == std::vector<char>{1, 0, 1}));  // start can be stopped on; the noStop cell cannot
}

static void test_reach_matches_path_cost() {
    Query q = make_query({"12111", "11111"}, {0, 0}, {0, 0}, 1000);
    ReachResult r = reach(q);
    for (int y = 0; y < 2; ++y) {
        for (int x = 0; x < 5; ++x) {
            q.goal = {x, y};
            PathResult p = find_path(q);
            CHECK(p.found && p.cost == r.costs[static_cast<std::size_t>(y * 5 + x)]);
        }
    }
}

int main() {
    test_parse_grid();
    test_zigzag_example();
    test_start_equals_goal();
    test_goal_blocked_and_no_stop();
    test_pass_through_no_stop();
    test_budget_edges();
    test_enclosed_goal();
    test_corner_cutting();
    test_true_diagonal();
    test_alternating();
    test_multipliers();
    test_tie_breaking();
    test_expansion_cap();
    test_big_unreachable_goal();
    test_reach_example();
    test_reach_budget_zero_and_no_stop();
    test_reach_matches_path_cost();

    if (failures == 0) {
        std::cout << "All tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
