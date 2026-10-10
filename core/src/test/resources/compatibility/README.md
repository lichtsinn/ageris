# Frozen configuration files

Each folder holds configuration files in the format of one Ageris release. `FileFormatCompatibilityTest` loads them with the
current code and checks the answers for a fixed set of callers.

Never edit a file in a folder of a released version. If the test fails, the code has changed what an existing file means, and
the code is what has to be fixed. With each release, add a folder with files that use what that release added to the format.
