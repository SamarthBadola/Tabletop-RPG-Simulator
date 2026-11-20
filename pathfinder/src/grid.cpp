#include <cstddef>

#include "pathfinder/model.hpp"

namespace pf {

std::optional<Grid> parse_grid(const std::vector<std::string>& rows, std::string& error) {
    if (rows.empty() || rows.size() > static_cast<std::size_t>(kMaxSide)) {
        error = "height must be 1 to 200";
        return std::nullopt;
    }
    const std::size_t width = rows.front().size();
    if (width == 0 || width > static_cast<std::size_t>(kMaxSide)) {
        error = "width must be 1 to 200";
        return std::nullopt;
    }

    Grid grid;
    grid.width = static_cast<int>(width);
    grid.height = static_cast<int>(rows.size());
    grid.multipliers.reserve(width * rows.size());

    for (std::size_t y = 0; y < rows.size(); ++y) {
        if (rows[y].size() != width) {
            error = "row " + std::to_string(y) + " has the wrong length";
            return std::nullopt;
        }
        for (const char c : rows[y]) {
            if (c < '0' || c > '9') {
                error = "row " + std::to_string(y) + " has a character that is not 0 to 9";
                return std::nullopt;
            }
            grid.multipliers.push_back(static_cast<std::uint8_t>(c - '0'));
        }
    }
    return grid;
}

}  // namespace pf
