using System;
using Xunit;

[assembly: CLSCompliant(false)]
namespace Ca.Pranavpatel.Algo.GridPointCode.Tests {
    /// <summary>
    /// Tests for GPC class
    /// </summary>
    public class TestGPC {

        /// <summary>
        /// Tests encoding and decoding for minimum zero values.
        /// </summary>
        [Fact]
        public void TestMinZero() {
            Assert.Equal("#DCCC-CCCC-CCC", GPC.Encode(0, 0));
            Assert.Equal((0, 0), GPC.Decode("#DCCC-CCCC-CCC"));
        }

        /// <summary>
        /// Tests encoding and decoding for minimum positive values.
        /// </summary>
        [Fact]
        public void TestMin1() {
            Assert.Equal("#DCCC-CCCC-CCR", GPC.Encode(0.00001, 0.00001));
            Assert.Equal((0.00001, 0.00001), GPC.Decode("#DCCC-CCCC-CCR"));
        }

        /// <summary>
        /// Tests encoding and decoding for minimum negative latitude and positive longitude values.
        /// </summary>
        [Fact]
        public void TestMin2() {
            Assert.Equal("#DCCD-7Y5W-LLH", GPC.Encode(-0.00001, 0.00001));
            Assert.Equal((-0.00001, 0.00001), GPC.Decode("#DCCD-7Y5W-LLH"));
        }

        /// <summary>
        /// Tests encoding and decoding for minimum positive latitude and negative longitude values.
        /// </summary>
        [Fact]
        public void TestMin3() {
            Assert.Equal("#DCCC-8473-0G4", GPC.Encode(0.00001, -0.00001));
            Assert.Equal((0.00001, -0.00001), GPC.Decode("#DCCC-8473-0G4"));
        }

        /// <summary>
        /// Tests encoding and decoding for minimum negative latitude and longitude values.
        /// </summary>
        [Fact]
        public void TestMin4() {
            Assert.Equal("#DCCG-5K1D-WV7", GPC.Encode(-0.00001, -0.00001));
            Assert.Equal((-0.00001, -0.00001), GPC.Decode("#DCCG-5K1D-WV7"));
        }

        /// <summary>
        /// Tests encoding and decoding for maximum positive latitude and longitude values.
        /// </summary>
        [Fact]
        public void TestMax1() {
            Assert.Equal("#HG9K-PCVH-DPV", GPC.Encode(89.99999, 179.99999));
            Assert.Equal((89.99999, 179.99999), GPC.Decode("#HG9K-PCVH-DPV"));
        }

        /// <summary>
        /// Tests encoding and decoding for maximum negative latitude and positive longitude values.
        /// </summary>
        [Fact]
        public void TestMax2() {
            Assert.Equal("#HG9N-KTKR-83Y", GPC.Encode(-89.99999, 179.99999));
            Assert.Equal((-89.99999, 179.99999), GPC.Decode("#HG9N-KTKR-83Y"));
        }

        /// <summary>
        /// Tests encoding and decoding for maximum positive latitude and negative longitude values.
        /// </summary>
        [Fact]
        public void TestMax3() {
            Assert.Equal("#HG9M-L0M1-M0K", GPC.Encode(89.99999, -179.99999));
            Assert.Equal((89.99999, -179.99999), GPC.Decode("#HG9M-L0M1-M0K"));
        }

        /// <summary>
        /// Tests encoding and decoding for maximum negative latitude and longitude values.
        /// </summary>
        [Fact]
        public void TestMax4() {
            Assert.Equal("#HG9P-JLHJ-X69", GPC.Encode(-89.99999, -179.99999));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("#HG9P-JLHJ-X69"));
        }

        /// <summary>
        /// Tests encoding and decoding with truncation of latitude and longitude values.
        /// </summary>
        [Fact]
        public void TestTruncate() {
            Assert.Equal("#FYGC-MF89-XH2", GPC.Encode(-12.1234567, -123.1234567));
            Assert.Equal((-12.12345, -123.12345), GPC.Decode("#FYGC-MF89-XH2"));
        }

        /// <summary>
        /// Tests that encoding throws an ArgumentOutOfRangeException for latitude values outside the valid range.
        /// </summary>
        /// <param name="latitude">The latitude value to test.</param>
        /// <param name="longitude">The longitude value to test.</param>
        [Theory]
        [InlineData(-90, -123)]
        [InlineData(90, 123)]
        public void TestLatitude(double latitude, double longitude) {
            Action act = () => GPC.Encode(latitude, longitude);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("LATITUDE: value out of valid range. (Parameter 'LATITUDE')", ex.Message);
        }

        /// <summary>
        /// Tests that encoding throws an ArgumentOutOfRangeException for longitude values outside the valid range.
        /// </summary>
        /// <param name="latitude">The latitude value to test.</param>
        /// <param name="longitude">The longitude value to test.</param>
        [Theory]
        [InlineData(-12, -180)]
        [InlineData(12, 180)]
        public void TestLongitude(double latitude, double longitude) {
            Action act = () => GPC.Encode(latitude, longitude);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("LONGITUDE: value out of valid range. (Parameter 'LONGITUDE')", ex.Message);
        }

        /// <summary>
        /// Tests encoding and decoding of GPC with formatted output.
        /// </summary>
        [Fact]
        public void TestGPCFormatted() {
            Assert.Equal("#HG9P-JLHJ-X69", GPC.Encode(-89.99999, -179.99999, true));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("#HG9P-JLHJ-X69"));
        }

        /// <summary>
        /// Tests encoding and decoding of GPC with unformatted output.
        /// </summary>
        [Fact]
        public void TestGPCUnformatted() {
            Assert.Equal("HG9PJLHJX69", GPC.Encode(-89.99999, -179.99999, false));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("HG9PJLHJX69"));
        }

        /// <summary>
        /// Tests that decoding throws an ArgumentNullException for null or empty grid point codes.
        /// </summary>
        /// <param name="gridPointCode">The grid point code to test.</param>
        [Theory]
        [InlineData(null)]
        [InlineData("")]
        [InlineData("    ")]
        public void TestGPCNull(string gridPointCode) {
            Action act = () => GPC.Decode(gridPointCode);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentNullException>(ex);
            Assert.Equal("GPC_NULL: Invalid GPC. (Parameter 'gridPointCode')", ex.Message);
        }

        /// <summary>
        /// Tests that decoding throws an ArgumentOutOfRangeException for grid point codes with invalid length.
        /// </summary>
        /// <param name="gridPointCode">The grid point code to test.</param>
        [Theory]
        [InlineData("#HG9P-JLHJ-X696")]
        [InlineData("#HG9P-JLHJ-X6")]
        public void TestGPCLength(string gridPointCode) {
            Action act = () => GPC.Decode(gridPointCode);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("GPC_LENGTH: Invalid GPC. (Parameter 'gridPointCode')", ex.Message);
        }

        /// <summary>
        /// Tests that decoding throws an ArgumentOutOfRangeException for grid point codes with invalid characters.
        /// </summary>
        /// <param name="gridPointCode">The grid point code to test.</param>
        [Theory]
        [InlineData("#HG9P-JLHJ-A69")]
        [InlineData("#HG9P-JLHJ-E69")]
        public void TestGPCChar(string gridPointCode) {
            Action act = () => GPC.Decode(gridPointCode);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("GPC_CHAR: Invalid GPC. (Parameter 'gridPointCode')", ex.Message);
        }

        /// <summary>
        /// Tests that decoding throws an ArgumentOutOfRangeException for grid point codes 
        /// that are valid in format and character set but result in latitude/longitude values 
        /// outside the acceptable range.
        /// </summary>
        /// <param name="gridPointCode">The grid point code to test.</param>
        [Theory]
        [InlineData("#HG9P-JLHJ-X7C")]
        [InlineData("#JG9P-JLHJ-X7C")]
        public void TestGPCRange(string gridPointCode) {
            Action act = () => GPC.Decode(gridPointCode);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("GPC_RANGE: Invalid GPC. (Parameter 'gridPointCode')", ex.Message);
        }

    }
}
