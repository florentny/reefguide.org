'use strict';

(function() {
    var e = React.createElement;

    function countSpecies(node) {
        var count = node.species ? node.species.length : 0;
        if (node.children) {
            node.children.forEach(function(child) {
                count += countSpecies(child);
            });
        }
        return count;
    }

    function TaxonomyNode(props) {
        var node = props.node;
        var depth = props.depth;
        var selectedName = props.selectedName;
        var onSelect = props.onSelect;

        var expandedState = React.useState(false);
        var expanded = expandedState[0];
        var setExpanded = expandedState[1];

        var hasChildren = node.children && node.children.length > 0;
        var hasSpecies = node.species && node.species.length > 0;
        var speciesCount = countSpecies(node);
        var isSelected = selectedName && selectedName === node.name;

        var indent = { paddingLeft: (depth * 10) + 'px' };

        function handleToggle(ev) {
            ev.stopPropagation();
            setExpanded(!expanded);
        }

        function handleSelect(ev) {
            ev.stopPropagation();
            onSelect(node);
        }

        var toggleSymbol = (hasChildren || hasSpecies)
            ? (expanded ? '\u25be' : '\u25b8')
            : '\u00a0';

        var rowClass = 'taxon-row' + (isSelected ? ' taxon-selected' : '');

        return e('div', null,
            e('div', { className: rowClass, style: indent },
                e('span', {
                    className: 'taxon-toggle',
                    onClick: (hasChildren || hasSpecies) ? handleToggle : null
                }, toggleSymbol),
                e('span', { onClick: handleSelect },
                    e('span', { className: 'taxon-rank' }, node.rank + '\u00a0'),
                    node.name,
                    e('span', { className: 'taxon-count' }, '\u00a0(' + speciesCount + ')')
                )
            ),
            expanded ? e(React.Fragment, null,
                (node.children || []).map(function(child, i) {
                    return e(TaxonomyNode, {
                        key: i,
                        node: child,
                        depth: depth + 1,
                        selectedName: selectedName,
                        onSelect: onSelect
                    });
                }),
                (node.species || []).map(function(sp, i) {
                    var spIsSelected = selectedName === sp.id;
                    return e('div', {
                        key: 'sp-' + i,
                        style: { paddingLeft: ((depth + 1) * 10) + 'px' },
                        className: 'taxon-species-item' + (spIsSelected ? ' taxon-selected' : ''),
                        onClick: function() {
                            onSelect({ name: sp.id, rank: 'Species', species: [sp], children: [] });
                        }
                    }, '\u2022\u00a0' + sp.name + ' (' + sp.sname + ')');
                })
            ) : null
        );
    }

    function TaxonomyTree(props) {
        var data = props.data;
        var selectedName = props.selectedName;
        var onSelect = props.onSelect;

        if (!data) {
            return e('div', { style: { padding: '8px', color: '#888', fontStyle: 'italic' } }, 'Loading\u2026');
        }

        // Skip the root node (Biota/Domain), render its children directly
        var topNodes = data.children && data.children.length > 0 ? data.children : [data];

        return e('div', { className: 'taxonomy-tree' },
            topNodes.map(function(node, i) {
                return e(TaxonomyNode, {
                    key: i,
                    node: node,
                    depth: 0,
                    selectedName: selectedName,
                    onSelect: onSelect
                });
            })
        );
    }

    window.TaxonomyTree = TaxonomyTree;
})();
