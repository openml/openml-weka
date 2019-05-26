[![Build Status](https://travis-ci.org/openml/openml-weka.svg?branch=master)](https://travis-ci.org/openml/openml-weka)

The OpenmlWeka package

(Pre)
    Update Description.props

(Version 2019, February)

(1) run ant from package directory
    ant -f build_package.xml make_package -Dpackage=OpenMLWeka

(*) Note that this ant script uses the lib and dependencies folders, and not
    the maven dependencies. Effort needs to be made to keep these in sync with
    the propor maven dependencies (or update the ant script)

(Version 2016) Ant installation

(1) Download Weka from SVN, trunk folder
    https://svn.cms.waikato.ac.nz/svn/weka/trunk

(2) Put OpenmlWeka package folder in the following folder:
    <svnroot>/packages/external
    (Or use symlink, ln -s )

(3) Run from build file the <svnroot>/weka folder:
    a) ant (to make weka itself, which is a dependency)
    b) ant make_external -DpackageName=openml-weka (build the package)

(4) Package available in OpenmlWeka folder.


(Version 2015) Plain ant installation:

ant make_external -DpackageName=OpenmlWeka
