
'use strict';

(function() {
    const e = React.createElement;

    const DEFAULT_CONFIG = { imgWidth: 240, imgWidthAct: 216, thumbClass: 'thumb', cellClass: 'celltd' };

    function getConfig() {
        return window.currentConfig || DEFAULT_CONFIG;
    }

    function calcNumCol(imgWidth) {
        const offset = typeof panelOffset !== 'undefined' ? panelOffset : 200;
        const cols = Math.floor((window.innerWidth - offset) / imgWidth);
        return Math.max(1, cols);
    }

    function renderSection(section, numCol, config, showSci) {
        const imgWidth = config.imgWidth;
        const imgWidthAct = config.imgWidthAct;
        const thumbClass = config.thumbClass;
        const cellClass = config.cellClass;
        const colTemplate = 'repeat(' + numCol + ', 1fr)';

        const species = section.species || [];
        if (species.length === 0) return null;

        const taxonParam = (section.breadcrumb && section.breadcrumb.length > 0)
            ? '?taxon=' + encodeURIComponent(section.breadcrumb[section.breadcrumb.length - 1].name)
            : '';

        const rows = [];
        for (let i = 0; i < species.length; i += numCol) {
            rows.push(species.slice(i, i + numCol));
        }

        return e('div', null,
            (section.breadcrumb && section.breadcrumb.length > 0) ? e('div', { className: 'taxon-grid-header' },
                section.breadcrumb.map(function(crumb, i) {
                    const isLast = i === section.breadcrumb.length - 1;
                    const crumbContent = e('a', { href: '#taxon=' + encodeURIComponent(crumb.name) },
                        e('span', { style: { fontStyle: 'italic' } }, crumb.name),
                        crumb.rank ? e('span', { style: { fontStyle: 'normal', color: '#b8a84a' } }, '\u00a0(' + crumb.rank + ')') : null,
                        crumb.category ? e('span', { style: { fontStyle: 'normal' } }, '\u00a0[' + crumb.category + ']') : null
                    );
                    return isLast
                        ? e('span', { key: i }, crumbContent)
                        : e('span', { key: i }, crumbContent, e('span', null, '\u00a0\u2192\u00a0'));
                })
            ) : null,
            rows.map(function(row, ri) {
                const imageRow = e('div', {
                    key: 'img-' + ri,
                    className: 'grid-row',
                    style: { gridTemplateColumns: colTemplate }
                }, row.map(function(sp, ci) {
                    const thumbUrl = 'pix/thumb/' + sp.id + sp.thumb + '.jpg';
                    const thumbSrc = thumbUrl.replace('thumb', thumbClass);
                    const spUrl = sp.id + '.html' + taxonParam;
                    return e('div', { key: ci, className: cellClass },
                        e('a', { href: spUrl },
                            e('img', {
                                className: 'selframe',
                                src: thumbSrc,
                                width: imgWidthAct,
                                alt: sp.name + (sp.sname ? ' - ' + sp.sname : ''),
                                title: sp.name + (sp.sname ? ' - ' + sp.sname : '')
                            })
                        )
                    );
                }));

                const nameRow = e('div', {
                    key: 'name-' + ri,
                    className: 'grid-row',
                    style: { gridTemplateColumns: colTemplate }
                }, row.map(function(sp, ci) {
                    const spUrl = sp.id + '.html' + taxonParam;
                    const hasSci = sp.sname && sp.sname.trim().length > 0;
                    const displayName = showSci
                        ? (hasSci ? sp.sname : 'undetermined [' + sp.name + ']')
                        : sp.name;
                    const isItalic = showSci && hasSci;
                    return e('div', { key: ci, className: 'nameid' },
                        e('div', { className: 'nameid' },
                            e('a', { className: 'nameid', href: spUrl,
                                style: isItalic ? { fontStyle: 'italic' } : null
                            }, displayName)
                        )
                    );
                }));

                return e(React.Fragment, { key: ri }, imageRow, nameRow);
            })
        );
    }

    function TaxonomyGrid(props) {
        const sections = props.sections;

        // Re-render on resize or size setting change
        const sizeState = React.useState(0);
        const setSizeKey = sizeState[1];
        React.useEffect(function() {
            function onResize() { setSizeKey(function(k) { return k + 1; }); }
            window.addEventListener('resize', onResize);
            window.addEventListener('reefsize', onResize);
            return function() {
                window.removeEventListener('resize', onResize);
                window.removeEventListener('reefsize', onResize);
            };
        }, []);

        const nameToggleState = React.useState(function() {
            return localStorage.getItem('reefNameMode') === 'sci';
        });
        const showSci = nameToggleState[0];
        const setShowSci = nameToggleState[1];

        function setNameMode(sci) {
            localStorage.setItem('reefNameMode', sci ? 'sci' : 'common');
            setShowSci(sci);
        }

        const nameToggle = e('div', { className: 'taxgrid-name-toggle' },
            e('label', null,
                e('input', { type: 'radio', name: 'taxgrid-name', checked: !showSci,
                    onChange: function() { setNameMode(false); } }),
                'Common name'
            ),
            e('label', null,
                e('input', { type: 'radio', name: 'taxgrid-name', checked: showSci,
                    onChange: function() { setNameMode(true); } }),
                'Scientific name'
            )
        );

        if (!sections || sections.length === 0) {
            return e('div', null, nameToggle,
                e('div', { className: 'taxon-grid-empty' }, 'Select a taxon in the tree to view species.')
            );
        }

        const totalSpecies = sections.reduce(function(n, s) { return n + (s.species ? s.species.length : 0); }, 0);
        if (totalSpecies === 0) {
            return e('div', null, nameToggle,
                e('div', { className: 'taxon-grid-empty' }, 'Select a taxon in the tree to view species.')
            );
        }

        const config = getConfig();
        const numCol = calcNumCol(config.imgWidth);

        return e('div', null,
            nameToggle,
            sections.map(function(section, si) {
                return e(React.Fragment, { key: si }, renderSection(section, numCol, config, showSci));
            })
        );
    }

    window.TaxonomyGrid = TaxonomyGrid;
})();
