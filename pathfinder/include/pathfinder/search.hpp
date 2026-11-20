#pragma once

#include <vector>

#include "pathfinder/model.hpp"

namespace pf {

inline constexpr int kExpansionCap = 100000;  // spec section 6.1, step 3

// Everything one request carries (spec section 5.2 and 5.3).
struct Query {
    Grid grid;
    std::vector<Cell> no_stop;  // may be passed through, never ended on
    Metric metric = Metric::EqualCost;
    Corners corners = Corners::ForbidIfBlocked;
    int parity = 0;  // 0 or 1, only used by Metric::Alternating
    Cell start;
    Cell goal;  // only used by find_path
    int budget = 0;
};

enum class Reason { None, NoPathWithinBudget, GoalBlocked, GoalNoStop, ExpansionCap };

struct PathResult {
    bool found = false;
    Reason reason = Reason::None;  // only meaningful when found is false
    std::vector<Cell> path;        // start ... goal
    int cost = 0;
    int expansions = 0;
};

struct ReachResult {
    std::vector<int> costs;  // row-major, cheapest cost to reach each cell, -1 = unreachable
    std::vector<char> stop;  // row-major, 1 = the move may end on this cell
};

// A*, exactly as spec section 6.1.
PathResult find_path(const Query& query, int expansion_cap = kExpansionCap);

// Dijkstra, exactly as spec section 6.2.
ReachResult reach(const Query& query);

}  // namespace pf
