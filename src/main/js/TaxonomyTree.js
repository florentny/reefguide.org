'use strict';

(function() {
    const e = React.createElement;

    const PRE_EXPAND_RANKS = ['domain', 'kingdom', 'subkingdom', 'infrakingdom', 'superphylum', 'superdivision'];

    function shouldPreExpand(node) {
        return node.rank && PRE_EXPAND_RANKS.indexOf(node.rank.toLowerCase()) !== -1;
    }

    function countSpecies(node) {
        let count = node.species ? node.species.length : 0;
        if (node.children) {
            node.children.forEach(function(child) {
                count += countSpecies(child);
            });
        }
        return count;
    }

    function TaxonomyNode(props) {
        const node = props.node;
        const depth = props.depth;
        const selectedName = props.selectedName;
        const onSelect = props.onSelect;
        const autoExpand = props.autoExpand;
        const expandPath = props.expandPath;

        const expandedState = React.useState(function() { return shouldPreExpand(node); });
        const expanded = expandedState[0];
        const setExpanded = expandedState[1];

        // Tracks whether this node was expanded by user action (vs pre-expansion).
        // autoExpand propagation only chains through nodes the user has expanded.
        const userExpandedState = React.useState(false);
        const userExpanded = userExpandedState[0];
        const setUserExpanded = userExpandedState[1];

        React.useEffect(function() {
            if (autoExpand) {
                setExpanded(true);
                setUserExpanded(true);
            }
        }, [autoExpand]);

        React.useEffect(function() {
            if (expandPath && expandPath[node.name]) setExpanded(true);
        }, [expandPath]);

        const hasChildren = node.children && node.children.length > 0;
        const hasSpecies = node.species && node.species.length > 0;
        const speciesCount = countSpecies(node);
        const isSelected = selectedName && selectedName === node.name;
        const singleChild = (node.children || []).length === 1 && !(node.species && node.species.length > 0);

        const indent = { paddingLeft: (depth * 10) + 'px' };

        function handleToggle(ev) {
            ev.stopPropagation();
            setUserExpanded(true);
            setExpanded(!expanded);
        }

        function handleSelect(ev) {
            ev.stopPropagation();
            if (hasChildren || hasSpecies) {
                setExpanded(true);
                setUserExpanded(true);
            }
            onSelect(node);
        }

        const toggleSymbol = (hasChildren || hasSpecies)
            ? (expanded ? '\u25be' : '\u25b8')
            : '\u00a0';

        const rowClass = 'taxon-row' + (isSelected ? ' taxon-selected' : '');

        return e('div', null,
            e('div', { className: rowClass, style: indent },
                e('span', {
                    className: 'taxon-toggle',
                    onClick: (hasChildren || hasSpecies) ? handleToggle : null
                }, toggleSymbol),
                e('span', { onClick: handleSelect },
                    e('span', { className: 'taxon-rank' }, node.rank + '\u00a0'),
                    e('span', { className: 'taxon-name' }, node.name),
                    node.category ? e('span', { className: 'taxon-category' }, '\u00a0\u2013\u00a0' + node.category) : null,
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
                        onSelect: onSelect,
                        autoExpand: singleChild && userExpanded,
                        expandPath: expandPath
                    });
                }),
                (node.species || []).map(function(sp, i) {
                    return e('div', {
                        key: 'sp-' + i,
                        style: { paddingLeft: ((depth + 1) * 10) + 'px' },
                        className: 'taxon-species-item',
                        onClick: function(ev) { ev.stopPropagation(); onSelect(node); }
                    }, (function() {
                        const parts = (sp.sname || '').split(' ');
                        const abbrev = parts.length > 1 ? parts[0][0] + '.\u00a0' + parts.slice(1).join(' ') : sp.sname;
                        return '\u2022\u00a0' + abbrev + (sp.name ? '\u00a0(' + sp.name + ')' : '');
                    })());
                })
            ) : null
        );
    }

    function TaxonomyTree(props) {
        const data = props.data;
        const selectedName = props.selectedName;
        const onSelect = props.onSelect;
        const expandPath = props.expandPath;

        if (!data) {
            return e('div', { style: { padding: '8px', color: '#888', fontStyle: 'italic' } }, 'Loading\u2026');
        }

        // Skip the root node (Biota/Domain), render its children directly
        const topNodes = data.children && data.children.length > 0 ? data.children : [data];

        return e('div', { className: 'taxonomy-tree' },
            topNodes.map(function(node, i) {
                return e(TaxonomyNode, {
                    key: i,
                    node: node,
                    depth: 0,
                    selectedName: selectedName,
                    onSelect: onSelect,
                    expandPath: expandPath
                });
            })
        );
    }

    window.TaxonomyTree = TaxonomyTree;
})();
