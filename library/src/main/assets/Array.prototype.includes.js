if (!Array.prototype.includes) {
    Array.prototype.includes = function(searchEl, fromIndex) {
        var i = fromIndex ? fromIndex : 0;
        for (i; i < this.length; i++) {
            if (this[i] === searchEl) {
                return true;
            }
        }

        return false;
    }
}