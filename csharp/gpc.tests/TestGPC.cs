using System;
using Xunit;

[assembly: CLSCompliant(false)]
namespace Ninja.Pranav.Algorithms.GridPointCode.Tests {
    /// <summary>
    /// Tests for GPC class
    /// </summary>
    public class TestGPC {

        [Fact]
        public void TestMinZero() {
            Assert.Equal("#DCCC-CCCC-CCC", GPC.Encode(0, 0));
            Assert.Equal((0, 0), GPC.Decode("#DCCC-CCCC-CCC"));
        }

        [Fact]
        public void TestMin1() {
            Assert.Equal("#DCCC-CCCC-CCR", GPC.Encode(0.00001, 0.00001));
            Assert.Equal((0.00001, 0.00001), GPC.Decode("#DCCC-CCCC-CCR"));
        }

        [Fact]
        public void TestMin2() {
            Assert.Equal("#DCCD-7Y5W-LLH", GPC.Encode(-0.00001, 0.00001));
            Assert.Equal((-0.00001, 0.00001), GPC.Decode("#DCCD-7Y5W-LLH"));
        }

        [Fact]
        public void TestMin3() {
            Assert.Equal("#DCCC-8473-0G4", GPC.Encode(0.00001, -0.00001));
            Assert.Equal((0.00001, -0.00001), GPC.Decode("#DCCC-8473-0G4"));
        }

        [Fact]
        public void TestMin4() {
            Assert.Equal("#DCCG-5K1D-WV7", GPC.Encode(-0.00001, -0.00001));
            Assert.Equal((-0.00001, -0.00001), GPC.Decode("#DCCG-5K1D-WV7"));
        }

        [Fact]
        public void TestMax1() {
            Assert.Equal("#HG9K-PCVH-DPV", GPC.Encode(89.99999, 179.99999));
            Assert.Equal((89.99999, 179.99999), GPC.Decode("#HG9K-PCVH-DPV"));
        }

        [Fact]
        public void TestMax2() {
            Assert.Equal("#HG9N-KTKR-83Y", GPC.Encode(-89.99999, 179.99999));
            Assert.Equal((-89.99999, 179.99999), GPC.Decode("#HG9N-KTKR-83Y"));
        }

        [Fact]
        public void TestMax3() {
            Assert.Equal("#HG9M-L0M1-M0K", GPC.Encode(89.99999, -179.99999));
            Assert.Equal((89.99999, -179.99999), GPC.Decode("#HG9M-L0M1-M0K"));
        }

        [Fact]
        public void TestMax4() {
            Assert.Equal("#HG9P-JLHJ-X69", GPC.Encode(-89.99999, -179.99999));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("#HG9P-JLHJ-X69"));
        }

        [Fact]
        public void TestTruncate() {
            Assert.Equal("#FYGC-MF89-XH2", GPC.Encode(-12.1234567, -123.1234567));
            Assert.Equal((-12.12345, -123.12345), GPC.Decode("#FYGC-MF89-XH2"));
        }

        [Theory]
        [InlineData(-90, -123)]
        [InlineData(90, 123)]
        public void TestLATITUDE(double latitude, double longitude) {
            Action act = () => GPC.Encode(latitude, longitude);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("LATITUDE: value out of valid range. (Parameter 'latitude')", ex.Message);
        }

        [Theory]
        [InlineData(-12, -180)]
        [InlineData(12, 180)]
        public void TestLONGITUDE(double latitude, double longitude) {
            Action act = () => GPC.Encode(latitude, longitude);
            Exception ex = Record.Exception(act);
            Assert.NotNull(ex);
            _ = Assert.IsType<ArgumentOutOfRangeException>(ex);
            Assert.Equal("LONGITUDE: value out of valid range. (Parameter 'longitude')", ex.Message);
        }

        [Fact]
        public void TestGPCFormatted() {
            Assert.Equal("#HG9P-JLHJ-X69", GPC.Encode(-89.99999, -179.99999, true));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("#HG9P-JLHJ-X69"));
        }

        [Fact]
        public void TestGPCUnformatted() {
            Assert.Equal("HG9PJLHJX69", GPC.Encode(-89.99999, -179.99999, false));
            Assert.Equal((-89.99999, -179.99999), GPC.Decode("HG9PJLHJX69"));
        }

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
