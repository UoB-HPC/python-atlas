package uob_hpc.python_atlas

import uob_hpc.python_atlas.Conda.CondaPackagePinning

class CondaSuite extends munit.FunSuite {

  private val Basic = List(
    "numpy",
    "numpy 1.8*",
    "numpy 1.8.1",
    "numpy >=1.8",
    "numpy ==1.8.1",
    "numpy 1.8|1.8*",
    "numpy >=1.8,<2",
    "numpy >=1.8,<2|1.9",
    "numpy 1.8.1 py27_0",
    "numpy=1.8.1=py27_0"
  )

  private val Bang = List(
    "jsonpatch !=1.20,>=1.16",
    "matplotlib >=1.2.0,!=2.1.0,!=2.1.1",
    "numpy >=1.11,!=1.14.0,!=1.15.3",
    "numpy >=1.11.0,!=1.16.0,<1.22.0",
    "numpy >=1.13,!=1.16,!=1.17",
    "oauthlib !=2.0.3,!=2.0.4,!=2.0.5,<3.0.0,>=1.1.2",
    "osqp !=0.6.0,!=0.6.1",
    "pbr !=2.1.0,>=2.0.0",
    "pillow >=5.3.0,!=8.3.0,!=8.3.1",
    "requests !=2.12.2,!=2.13.0,>=2.10.0",
    "requests >=2.5.1,!=2.6.1,!=2.16.0,!=2.16.1",
    "retrying !=1.3.0,>=1.2.3",
    "scipy !=0.19.1,>=0.14.1",
    "scipy >=0.19,!=1.5.0,!=1.5",
    "sortedcontainers !=2.0.0,!=2.0.1",
    "uproot >=4.1.6,<5,!=4.2.4,!=4.3.0,!=4.3.1",
    "uvloop >=0.14.0,!=0.15.0,!=0.15.1",
    "virtualenv >=16.0.0,!=20.0.0,!=20.0.1,!=20.0.2,!=20.0.3,!=20.0.4,!=20.0.5,!=20.0.6,!=20.0.7",
    "websocket-client >=0.32.0,!=0.40.0,!=0.41.*,!=0.42.*",
    "wxpython !=4.0.2,!=4.0.3",
    "yt >=3.2,!=3.3.0,!=3.3.1"
  )

  private inline def testParses(s: String) = test(s"parse `$s`") {
    CondaPackagePinning(s) match {
      case Left(e)  => fail(e.toString)
      case Right(_) => ()
    }
  }

  Basic.foreach(testParses(_))
  Bang.foreach(testParses(_))

}
