#  Copyright 2017 Pranavkumar Patel
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""The one piece of arithmetic the plugin does for itself.

Deliberately free of any QGIS import, so it can be tested on a machine that has
no QGIS -- which is where it was written. Everything else in this plugin is a
thin wrapper around the library and around Processing, and has to be run inside
QGIS to mean anything; this is the part with a formula in it, so this is the
part that gets a test anybody can run.
"""

#: The grid, from section 3. Used to draw a cell coarser than level 10, which
#: `decodeToArea` will not do -- it takes a whole code, not a prefix.
ROWS = 7812500
COLUMNS = 11718750


def cell_box(GPC, code, level):
    """The boundaries of the cell a code falls in, at any level.

    Section 6.3 applied to the cell rather than to the finest code. At level 10
    this is bit-identical to `decodeToArea`, which is checked in the tests --
    it would be easy for a second copy of the arithmetic to drift, and this is
    a second copy of the arithmetic.
    """
    row, column = GPC.code_to_grid(GPC.cell(code, 10))
    span = 5 ** (10 - level)
    first_row = (row // span) * span
    first_column = (column // span) * span

    return (
        first_row * 180.0 / ROWS - 90.0,
        first_column * 360.0 / COLUMNS - 180.0,
        (first_row + span) * 180.0 / ROWS - 90.0,
        (first_column + span) * 360.0 / COLUMNS - 180.0,
    )
