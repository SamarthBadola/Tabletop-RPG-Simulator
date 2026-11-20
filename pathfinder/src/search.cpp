#include "pathfinder/search.hpp"

#include <algorithm>
#include <cstdlib>
#include <functional>
#include <limits>
#include <queue>
#include <tuple>

namespace pf {
namespace {

// The fixed neighbour order of spec section 3: N, E, S, W, NE, SE, SW, NW.
constexpr int kDx[8] = {0, 1, 0, -1, 1, 1, -1, -1};
constexpr int kDy[8] = {-1, 0, 1, 0, -1, 1, 1, -1};

constexpr int kInfinity = std::numeric_limits<int>::max();

struct Step {
    int x;
    int y;
    int parity;
    int cost;
};

int start_parity(const Query& q) { return q.metric == Metric::Alternating ? q.parity : 0; }

// A state is a cell plus a parity (spec section 6.1), so each cell has two states.
int state_index(const Grid& g, int x, int y, int parity) { return g.index(x, y) * 2 + parity; }

std::vector<char> no_stop_mask(const Query& q) {
    std::vector<char> mask(static_cast<std::size_t>(q.grid.cell_count()), 0);
    for (const Cell& c : q.no_stop) {
        if (q.grid.inside(c.x, c.y)) mask[static_cast<std::size_t>(q.grid.index(c.x, c.y))] = 1;
    }
    return mask;
}

// Calls fn once for every legal move out of (x, y), in the fixed neighbour order.
// Applies the grid edge, blocked cells, the corner rule and the step cost (spec 4.1 to 4.3).
template <typename Fn>
void for_each_step(const Query& q, int x, int y, int parity, Fn&& fn) {
    const Grid& g = q.grid;
    for (int d = 0; d < 8; ++d) {
        const int nx = x + kDx[d];
        const int ny = y + kDy[d];
        if (!g.inside(nx, ny) || g.blocked(nx, ny)) continue;

        const bool diagonal = kDx[d] != 0 && kDy[d] != 0;
        if (diagonal && q.corners == Corners::ForbidIfBlocked &&
            (g.blocked(nx, y) || g.blocked(x, ny))) {
            continue;
        }

        int base = 100;
        int next_parity = parity;
        if (diagonal) {
            switch (q.metric) {
                case Metric::EqualCost: base = 100; break;
                case Metric::TrueDiagonal: base = 141; break;
                case Metric::Alternating:
                    base = (parity == 0) ? 100 : 200;
                    next_parity = parity ^ 1;
                    break;
            }
        }
        fn(Step{nx, ny, next_parity, base * g.multiplier(nx, ny)});
    }
}

// One entry of the A* open set. The key decides which entry is expanded first:
// smallest f, then largest g, then smallest y, then smallest x, then smallest parity.
struct Node {
    int f;
    int g;
    int y;
    int x;
    int parity;
    std::tuple<int, int, int, int, int> key() const { return {f, -g, y, x, parity}; }
};

struct ExpandLater {
    bool operator()(const Node& a, const Node& b) const { return a.key() > b.key(); }
};

}  // namespace

PathResult find_path(const Query& q, int expansion_cap) {
    const Grid& g = q.grid;
    const Cell start = q.start;
    const Cell goal = q.goal;
    PathResult result;

    // The HTTP layer rejects coordinates outside the grid before we get here (spec 5.6).
    // This guard only keeps a programming mistake from reading outside the vectors.
    if (!g.inside(start.x, start.y) || !g.inside(goal.x, goal.y)) {
        result.reason = Reason::NoPathWithinBudget;
        return result;
    }

    if (start != goal) {
        if (g.blocked(goal.x, goal.y)) {
            result.reason = Reason::GoalBlocked;
            return result;
        }
        if (no_stop_mask(q)[static_cast<std::size_t>(g.index(goal.x, goal.y))]) {
            result.reason = Reason::GoalNoStop;
            return result;
        }
    }

    const auto state_count = static_cast<std::size_t>(g.cell_count()) * 2;
    std::vector<int> best(state_count, kInfinity);  // cheapest known cost to each state
    std::vector<int> parent(state_count, -1);       // the state we came from
    std::priority_queue<Node, std::vector<Node>, ExpandLater> open;

    const auto heuristic = [&](int x, int y) {
        return 100 * std::max(std::abs(goal.x - x), std::abs(goal.y - y));
    };

    const int first_parity = start_parity(q);
    best[static_cast<std::size_t>(state_index(g, start.x, start.y, first_parity))] = 0;
    open.push(Node{heuristic(start.x, start.y), 0, start.y, start.x, first_parity});

    while (!open.empty()) {
        const Node node = open.top();
        open.pop();
        const int state = state_index(g, node.x, node.y, node.parity);
        if (node.g > best[static_cast<std::size_t>(state)]) continue;  // stale entry

        ++result.expansions;
        if (node.x == goal.x && node.y == goal.y) {
            for (int s = state; s != -1; s = parent[static_cast<std::size_t>(s)]) {
                const int cell = s / 2;
                result.path.push_back(Cell{cell % g.width, cell / g.width});
            }
            std::reverse(result.path.begin(), result.path.end());
            result.found = true;
            result.cost = node.g;
            return result;
        }
        if (result.expansions >= expansion_cap) {
            result.reason = Reason::ExpansionCap;
            return result;
        }

        for_each_step(q, node.x, node.y, node.parity, [&](const Step& step) {
            const int next_g = node.g + step.cost;
            if (next_g > q.budget) return;
            const int next_state = state_index(g, step.x, step.y, step.parity);
            if (next_g < best[static_cast<std::size_t>(next_state)]) {
                best[static_cast<std::size_t>(next_state)] = next_g;
                parent[static_cast<std::size_t>(next_state)] = state;
                open.push(Node{next_g + heuristic(step.x, step.y), next_g, step.y, step.x, step.parity});
            }
        });
    }

    result.reason = Reason::NoPathWithinBudget;
    return result;
}

ReachResult reach(const Query& q) {
    const Grid& g = q.grid;
    ReachResult out;
    out.costs.assign(static_cast<std::size_t>(g.cell_count()), -1);
    out.stop.assign(static_cast<std::size_t>(g.cell_count()), 0);
    if (!g.inside(q.start.x, q.start.y)) return out;

    const std::vector<char> blocked_for_stop = no_stop_mask(q);
    std::vector<int> best(static_cast<std::size_t>(g.cell_count()) * 2, kInfinity);

    // (cost so far, y, x, parity); the smallest cost is taken first.
    using Item = std::tuple<int, int, int, int>;
    std::priority_queue<Item, std::vector<Item>, std::greater<Item>> open;

    const int first_parity = start_parity(q);
    best[static_cast<std::size_t>(state_index(g, q.start.x, q.start.y, first_parity))] = 0;
    open.emplace(0, q.start.y, q.start.x, first_parity);

    while (!open.empty()) {
        const Item item = open.top();
        open.pop();
        const int cost = std::get<0>(item);
        const int y = std::get<1>(item);
        const int x = std::get<2>(item);
        const int parity = std::get<3>(item);
        if (cost > best[static_cast<std::size_t>(state_index(g, x, y, parity))]) continue;

        for_each_step(q, x, y, parity, [&](const Step& step) {
            const int next_cost = cost + step.cost;
            if (next_cost > q.budget) return;
            const auto next_state = static_cast<std::size_t>(state_index(g, step.x, step.y, step.parity));
            if (next_cost < best[next_state]) {
                best[next_state] = next_cost;
                open.emplace(next_cost, step.y, step.x, step.parity);
            }
        });
    }

    for (int cell = 0; cell < g.cell_count(); ++cell) {
        const int cheapest = std::min(best[static_cast<std::size_t>(cell) * 2], best[static_cast<std::size_t>(cell) * 2 + 1]);
        if (cheapest == kInfinity) continue;
        out.costs[static_cast<std::size_t>(cell)] = cheapest;
        const bool is_start = cell == g.index(q.start.x, q.start.y);
        const bool can_stop = !g.blocked(cell % g.width, cell / g.width) && !blocked_for_stop[static_cast<std::size_t>(cell)];
        out.stop[static_cast<std::size_t>(cell)] = (is_start || can_stop) ? 1 : 0;
    }
    return out;
}

}  // namespace pf
