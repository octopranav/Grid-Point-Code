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

"""What puts the three algorithms in the Processing toolbox."""

from qgis.core import QgsProcessingProvider

from .algorithms import ALGORITHMS


class GridPointCodeProvider(QgsProcessingProvider):

    def loadAlgorithms(self):
        for algorithm in ALGORITHMS:
            self.addAlgorithm(algorithm())

    def id(self):
        return "gridpointcode"

    def name(self):
        return "Grid Point Code"

    def longName(self):
        return self.name()
