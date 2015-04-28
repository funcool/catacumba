#!/bin/sh
VERSION="devel"

lein doc
(cd doc; make)

rm -rf /tmp/catacumba-doc/
mkdir -p /tmp/catacumba-doc/
mv doc/index.html /tmp/catacumba-doc/
mv doc/api /tmp/catacumba-doc/api

git checkout gh-pages;

rm -rf ./$VERSION
mv /tmp/catacumba-doc/ ./$VERSION

git add --all ./$VERSION
git commit -a -m "Update ${VERSION} doc"
