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

"""The one error this package raises."""


class GPCError(ValueError):
    """Raised for a coordinate outside the domain or a code that will not decode.

    The reason code is the part to branch on. ``GPC_RESERVED`` is deliberately
    distinct from every invalid reason: a reserved code is well formed and may
    one day mean something, while an invalid one is a typing error.

    Reasons are ``LATITUDE`` and ``LONGITUDE`` for coordinates, and
    ``GPC_NULL``, ``GPC_LENGTH``, ``GPC_CHAR``, ``GPC_CHECK``, ``GPC_RESERVED``
    and ``GPC_RANGE`` for codes. The last covers both an eleven-character
    version 1 code out of range and an integer form outside 0 to 25^10 - 1.

    The locality API adds three more, none of which ``validate`` ever returns:
    ``GPC_LEVEL`` for a level outside 1 to 10, and ``GPC_DMS`` and ``GPC_GEO``
    for text the two coordinate parsers do not accept.

    Subclasses ``ValueError``, which is what version 1 raised, so existing
    ``except ValueError`` blocks keep working.
    """

    def __init__(self, reason: str, message: str = ""):
        super().__init__(message or (reason + ": Invalid GPC."))
        self.reason = reason
