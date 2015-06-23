all: doc

api:
	mkdir -p dist/latest/
	cd ..
	lein doc

doc:
	mkdir -p dist/latest/
	asciidoctor -a docinfo -a stylesheet! -o dist/latest/index.html content.adoc

github: api doc
	ghp-import -m "Generate documentation" -b gh-pages dist/
	git push origin gh-pages
