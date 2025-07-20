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

import re
from kombin_algo_pranavpatel_ca import Table

# Constants
MIN_LAT = -90
MAX_LAT = 90
MIN_LONG = -180
MAX_LONG = 180
MAX_POINT = 648_009_999_999_999
ELEVEN = 205_881_132_094_649
CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789"  # base27
GPC_LENGTH = 11

LatLongTable = Table(180, 360, True)

class GPC:
    """
    Provides methods to encode geographic coordinates into a custom Grid Point Code (GPC)
    and decode GPCs back into latitude and longitude values.

    The GPC format is a compact, base-27 encoded representation of geospatial locations,
    intended for simplified location referencing and validation.
    """

    @staticmethod
    def encode(latitude: float, longitude: float, formatted: bool = True) -> str:
        """
        Encodes the given latitude and longitude into a Grid Point Code (GPC).

        Args:
            latitude (float): Latitude in degrees.
            longitude (float): Longitude in degrees.
            formatted (bool): If True, returns formatted GPC (e.g., #XXXX-XXXX-XXX).

        Returns:
            str: Encoded GPC string.

        Raises:
            ValueError: If input coordinates are outside the valid range.
        """
        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise ValueError(f"{message}: value out of valid range.")
        
        point = GPC.get_point(latitude, longitude)
        grid_point_code = GPC.encode_point(point + ELEVEN)
        
        if formatted:
            grid_point_code = GPC.format_gpc(grid_point_code)
        return grid_point_code

    @staticmethod
    def is_valid_coordinates(latitude: float, longitude: float) -> tuple[bool, str]:
        """
        Validates whether the latitude and longitude values fall within the acceptable range.

        Args:
            latitude (float): Latitude in degrees.
            longitude (float): Longitude in degrees.

        Returns:
            tuple[bool, str]: Tuple containing validation result and invalid field name (if any).
        """
        if not (MIN_LAT < latitude < MAX_LAT):
            return False, "LATITUDE"
        if not (MIN_LONG < longitude < MAX_LONG):
            return False, "LONGITUDE"
        return True, ""

    @staticmethod
    def get_point(latitude: float, longitude: float) -> int:
        """
        Converts latitude and longitude into a unique integer point for GPC encoding.

        Args:
            latitude (float): Latitude in degrees.
            longitude (float): Longitude in degrees.

        Returns:
            int: Encoded point value.
        """
        lat7 = GPC.split_to_7(format(latitude, '.10f'))
        long7 = GPC.split_to_7(format(longitude, '.10f'))
        
        point = int(10 ** 10 * (
            LatLongTable.GetIndexOfElements(
                (lat7[1] * 2) + (1 if lat7[0] == -1 else 0),
                (long7[1] * 2) + (1 if long7[0] == -1 else 0)
            ) + 1
        ))
        
        power = 9
        for i in range(2, 7):
            point += int(10 ** power * lat7[i])
            power -= 1
            point += int(10 ** power * long7[i])
            power -= 1
        return point

    @staticmethod
    def split_to_7(coordinate: str) -> list[int]:
        """
        Splits a coordinate string into a 7-element list for encoding.

        The 7 elements include sign, integer part, and 5 digits of fractional precision.

        Args:
            coordinate (str): String representation of the coordinate.

        Returns:
            list[int]: 7-element list representing the coordinate.
        """
        coordinate = format(float(coordinate), '.10f')
        coord = [0] * 7
        coord[0] = -1 if coordinate.startswith("-") else 1
        coordinate = coordinate.lstrip("-+")
        fractional = ""
        
        if "." in coordinate:
            integer_part, fractional_part = coordinate.split(".")
            coord[1] = int(integer_part)
            fractional = fractional_part
        else:
            coord[1] = int(coordinate)
        
        fractional = (fractional + "00000")[:5]
        
        coord[2:7] = [int(fractional[i]) for i in range(5)]
        
        return coord

    @staticmethod
    def encode_point(point: int) -> str:
        """
        Encodes an integer point into a base-27 string using custom GPC characters.

        Args:
            point (int): The point to encode.

        Returns:
            str: Base-27 encoded GPC string.
        """
        gpc = ""
        while point > 0:
            gpc = CHARACTERS[point % 27] + gpc
            point //= 27
        return gpc

    @staticmethod
    def format_gpc(gpc: str) -> str:
        """
        Formats a base-27 GPC string into the standard representation (#XXXX-XXXX-XXX).

        Args:
            gpc (str): Unformatted GPC string.

        Returns:
            str: Formatted GPC string.
        """
        return f"#{gpc[:4]}-{gpc[4:8]}-{gpc[8:11]}"

    @staticmethod
    def decode(grid_point_code: str) -> tuple[float, float]:
        """
        Decodes a Grid Point Code (GPC) back into latitude and longitude.

        Args:
            grid_point_code (str): Formatted or unformatted GPC string.

        Returns:
            tuple[float, float]: Tuple of latitude and longitude.

        Raises:
            ValueError: If GPC is invalid or outside representable range.
        """
        if not grid_point_code or grid_point_code.isspace():
            raise ValueError("GPC_NULL: Invalid GPC.")
        
        clean = re.sub(r"[#\-\s]", "", grid_point_code.upper())
        
        valid, message = GPC.validate_gpc(clean)
        if not valid:
            raise ValueError(f"{message}: Invalid GPC.")
        
        point = GPC.decode_to_point(clean) - ELEVEN

        valid, message = GPC.validate_point(point)
        if not valid:
            raise ValueError(f"{message}: Invalid GPC.")
        
        return GPC.get_coordinates(point)

    @staticmethod
    def is_valid_gpc(grid_point_code: str) -> tuple[bool, str]:
        """
        Validates a GPC string by checking length, character set, and decoded range.

        Args:
            grid_point_code (str): GPC string to validate.

        Returns:
            tuple[bool, str]: Validation result and message.
        """
        clean = re.sub(r"[#\-\s]", "", grid_point_code.upper())
        if not clean:
            return False, "GPC_NULL"
        valid, msg = GPC.validate_gpc(clean)
        if not valid:
            return False, msg
        return GPC.validate_point(GPC.decode_to_point(clean) - ELEVEN)

    @staticmethod
    def validate_gpc(code: str) -> tuple[bool, str]:
        """
        Validates the structural correctness of a GPC string.

        Args:
            code (str): GPC code without formatting.

        Returns:
            tuple[bool, str]: Validation result and error type (if any).
        """
        if len(code) != GPC_LENGTH:
            return False, "GPC_LENGTH"
        if any(c not in CHARACTERS for c in code):
            return False, "GPC_CHAR"
        return True, ""

    @staticmethod
    def validate_point(point: int) -> tuple[bool, str]:
        """
        Validates if the decoded point lies within the maximum allowed range.

        Args:
            point (int): Integer point to validate.

        Returns:
            tuple[bool, str]: Validation result and message.
        """
        if point > MAX_POINT:
            return False, "GPC_RANGE"
        return True, ""

    @staticmethod
    def decode_to_point(code: str) -> int:
        """
        Converts a GPC string into its corresponding integer point.

        Args:
            code (str): Base-27 encoded GPC string.

        Returns:
            int: Decoded point.
        """
        point = 0
        for i in range(GPC_LENGTH):
            point *= 27
            character = code[i]
            point += CHARACTERS.index(character)
        return point

    @staticmethod
    def get_coordinates(point: int) -> tuple[float, float]:
        """
        Converts an integer point back into its latitude and longitude representation.

        Args:
            point (int): Integer point value.

        Returns:
            tuple[float, float]: Latitude and longitude.
        """
        latlong_index = int(point // 10**10)
        fractional = int(point - latlong_index * 10**10)

        lat7, long7 = GPC.split_to_7_from_point(latlong_index, fractional)

        power = 0
        temp_lat = 0
        temp_long = 0

        for i in range(6, 0, -1):
            temp_lat += lat7[i] * (10 ** power)
            temp_long += long7[i] * (10 ** power)
            power += 1

        lat = temp_lat / 10**5 * lat7[0]
        long = temp_long / 10**5 * long7[0]
        return (lat, long)

    @staticmethod
    def split_to_7_from_point(index: int, fractional: int) -> tuple[list[int], list[int]]:
        """
        Reconstructs 7-element lat/long lists from encoded point components.

        Args:
            index (int): Index representing quadrant-based location.
            fractional (int): Encoded fractional component of coordinates.

        Returns:
            tuple[list[int], list[int]]: 7-element lists for latitude and longitude.
        """
        long7 = [0] * 7
        lat7 = [0] * 7

        t_lat, t_long = LatLongTable.GetElementsAtIndex(index - 1)
        
        lat7[0] = -1 if t_lat % 2 else 1
        lat7[1] = (t_lat - 1) // 2 if lat7[0] == -1 else t_lat // 2

        long7[0] = -1 if t_long % 2 else 1
        long7[1] = (t_long - 1) // 2 if long7[0] == -1 else t_long // 2

        power = 9
        for i in range(2, 7):
            lat7[i] = (fractional // (10 ** power)) % 10
            power -= 1
            long7[i] = (fractional // (10 ** power)) % 10
            power -= 1

        return lat7, long7
