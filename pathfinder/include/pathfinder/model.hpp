#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace pf {

inline constexpr int kMaxSide = 200;  // spec section 5.2: width and height are 1 to 200

enum class Metric { EqualCost, TrueDiagonal, Alternating };
enum class Corners { ForbidIfBlocked, Allow };

// x is the column, y is the row, [0,0] is the top-left cell (spec section 3).
struct Cell {
    int x = 0;
    int y = 0;
    bool operator==(const Cell&) const = default;
};

// A rectangular map. Every cell holds a multiplier: 0 = blocked, 1 to 9 = cost multiplier.
struct Grid {
    int width = 0;
    int height = 0;
    std::vector<std::uint8_t> multipliers;  // row-major: index = y * width + x

    int cell_count() const { return width * height; }
    int index(int x, int y) const { return y * width + x; }
    bool inside(int x, int y) const { return x >= 0 && x < width && y >= 0 && y < height; }
    int multiplier(int x, int y) const { return multipliers[static_cast<std::size_t>(index(x, y))]; }
    bool blocked(int x, int y) const { return multiplier(x, y) == 0; }
};

// Turns rows such as {"12111", "11111"} into a Grid.
// On failure it returns std::nullopt and puts a short reason in `error`.
std::optional<Grid> parse_grid(const std::vector<std::string>& rows, std::string& error);

}  // namespace pf
