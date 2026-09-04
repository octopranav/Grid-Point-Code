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

"""The QGIS plugin entry point.

Nothing here imports qgis at module level, so `gridpointcode.geometry` can be
imported and tested on a machine with no QGIS on it.
"""


class GridPointCodePlugin:
    """A Processing provider and nothing else.

    No toolbar, no dock, no menu. Everything this does belongs in the toolbox,
    where it can be run in a model, in a batch, or from the command line --
    which a button cannot.
    """

    def __init__(self):
        self.provider = None

    def initProcessing(self):
        from qgis.core import QgsApplication

        from .provider import GridPointCodeProvider

        self.provider = GridPointCodeProvider()
        QgsApplication.processingRegistry().addProvider(self.provider)

    def initGui(self):
        self.initProcessing()

    def unload(self):
        if self.provider is not None:
            from qgis.core import QgsApplication

            QgsApplication.processingRegistry().removeProvider(self.provider)
            self.provider = None


def classFactory(iface):            # noqa: N802  (QGIS decides this name)
    del iface                       # a provider-only plugin has no use for it
    return GridPointCodePlugin()
