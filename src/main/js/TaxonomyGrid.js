
'use strict';

(function() {
    var e = React.createElement;

    var DEFAULT_CONFIG = { imgWidth: 240, imgWidthAct: 216, thumbClass: 'thumb', cellClass: 'celltd' };

    function getConfig() {
        return window.currentConfig || DEFAULT_CONFIG;
    }

    function calcNumCol(imgWidth) {
        var offset = typeof panelOffset !== 'undefined' ? panelOffset : 200;
        var cols = Math.floor((window.innerWidth - offset) / imgWidth);
        return Math.max(1, cols);
    }

    function TaxonomyGrid(props) {
        var species = props.species;
        var title = props.title;

        // Re-render on resize
        var sizeState = React.useState(0);
        var setSizeKey = sizeState[1];
        React.useEffect(function() {
            function onResize() { setSizeKey(function(k) { return k + 1; }); }
            window.addEventListener('resize', onResize);
            return function() { window.removeEventListener('resize', onResize); };
        }, []);

        if (!species || species.length === 0) {
            return e('div', { className: 'taxon-grid-empty' },
                title
                    ? 'No species found under ' + title + '.'
                    : 'Select a taxon in the tree to view species.'
            );
        }

        var config = getConfig();
        var imgWidth = config.imgWidth;
        var imgWidthAct = config.imgWidthAct;
        var thumbClass = config.thumbClass;
        var cellClass = config.cellClass;
        var numCol = calcNumCol(imgWidth);

        // Build rows: groups of numCol species
        var rows = [];
        for (var i = 0; i < species.length; i += numCol) {
            rows.push(species.slice(i, i + numCol));
        }

        var colTemplate = 'repeat(' + numCol + ', 1fr)';

        return e('div', null,
            title ? e('div', { className: 'taxon-grid-header' }, title) : null,
            rows.map(function(row, ri) {
                // Image row
                var imageRow = e('div', {
                    key: 'img-' + ri,
                    className: 'grid-row',
                    style: { gridTemplateColumns: colTemplate }
                }, row.map(function(sp, ci) {
                    var thumbUrl = 'pix/thumb/' + sp.id + sp.thumb + '.jpg';
                    var thumbSrc = thumbUrl.replace('thumb', thumbClass);
                    var spUrl = sp.id + '.html';
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

                // Name row
                var nameRow = e('div', {
                    key: 'name-' + ri,
                    className: 'grid-row',
                    style: { gridTemplateColumns: colTemplate }
                }, row.map(function(sp, ci) {
                    var spUrl = sp.id + '.html';
                    return e('div', { key: ci, className: 'nameid', style: { width: imgWidth + 'px' } },
                        e('div', { className: 'nameid' },
                            e('a', { className: 'nameid', href: spUrl }, sp.name)
                        )
                    );
                }));

                return e(React.Fragment, { key: ri }, imageRow, nameRow);
            })
        );
    }

    window.TaxonomyGrid = TaxonomyGrid;
})();
